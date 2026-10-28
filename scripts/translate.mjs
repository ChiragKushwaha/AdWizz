// Fills in AdWizz's translations with Google Translate, in one run. No key needed. (Adapted from pdf-editor.)
// Two sets of text, each one flat {"key": "text"} file per language:
//   app   app/src/main/assets/i18n/<lang>.json   the Android app
//   site  site/i18n/<lang>.json                  the website (then run: node scripts/build-site.mjs)
// Languages: i18n/languages.json.
//
//   node scripts/translate.mjs                               every language, only what is missing
//   node scripts/translate.mjs es fr                         only these languages
//   node scripts/translate.mjs --ns site                     only the website (or app)
//   node scripts/translate.mjs --all                         retranslate every string, not only missing ones
//   node scripts/translate.mjs --dry-run                     show what would be sent
//   node scripts/translate.mjs --concurrency 3
//   node scripts/translate.mjs --engine chrome               use Chrome's built-in on-device translator
//
// Engines: "google" (default) calls the endpoint translate.google.com uses; when Google starts answering with its
// "unusual traffic" page the run switches to "chrome" by itself. "chrome" opens the installed Google Chrome through
// Playwright and uses its Translator API, which downloads a language pack once and then runs on this machine with no
// limits (Chrome 138 or later; it does not cover every language, and those are reported).
//
// Only missing, empty or broken (wrong {placeholders}) strings are sent, so the run can be stopped and started again
// at any time; each finished chunk is written straight away. Plural keys get the forms the language needs (`_few`,
// `_many` …). {placeholders} are swapped for numbered tokens before sending and restored after; a string that comes
// back with different placeholders is retried once on its own and otherwise left out (English shows through for it),
// and is listed at the end. The app's I18nTest checks every language keeps the placeholders.
//
// This is machine translation: good enough to ship a language, but have a native speaker review the site* files
// (titles, descriptions, FAQ) when you can. {b}…{eb}, {i}…{ei} and {a…}…{ea} in site text are formatting tokens.

import { existsSync, mkdirSync, readFileSync, readdirSync, renameSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");

// ---- options ----------------------------------------------------------------------------------------------------

const argv = process.argv.slice(2);
const option = (name, fallback) => {
  const i = argv.indexOf(`--${name}`);
  return i >= 0 && argv[i + 1] && !argv[i + 1].startsWith("--") ? argv[i + 1] : fallback;
};
const flag = (name) => argv.includes(`--${name}`);
const valued = new Set(["ns", "concurrency", "engine"]);
const positional = argv.filter((a, i) => !a.startsWith("--") && !valued.has(argv[i - 1]?.slice(2)));

const CONCURRENCY = Number(option("concurrency", "3"));
/** Characters of English per request; Google's endpoint takes about 5,000. */
const CHUNK_CHARS = 3500;
let ENGINE = option("engine", "google");
const ALL = flag("all");
const DRY = flag("dry-run");

const LANGS = JSON.parse(readFileSync(join(root, "i18n", "languages.json"), "utf8"));
const LOCALES = LANGS.filter((l) => l.code !== "en").map((l) => ({ code: l.code, name: l.name }));
/** Google's code where it differs from ours. */
const GOOGLE_CODE = Object.fromEntries(LANGS.filter((l) => l.google).map((l) => [l.code, l.google]));
/** Where each set of text lives: <dir>/en.json is the source, <dir>/<lang>.json the translations. */
const NS_DIR = { app: join(root, "app", "src", "main", "assets", "i18n"), site: join(root, "site", "i18n") };
const NAMESPACES = Object.keys(NS_DIR).filter((ns) => !option("ns") || option("ns").split(",").includes(ns));
const languages = LOCALES.filter((l) => positional.length === 0 || positional.includes(l.code));
for (const code of positional) {
  if (!LOCALES.some((l) => l.code === code)) throw new Error(`Unknown language "${code}" (see i18n/languages.json)`);
}

// ---- JSON paths ---------------------------------------------------------------------------------------------------
// Paths are arrays of segments, because flat namespaces use dots inside their keys ("toolbar.undo").

const PLURAL = /^(.*)_(zero|one|two|few|many|other)$/;
const CATEGORY_ORDER = ["zero", "one", "two", "few", "many", "other"];

function leaves(value, path = [], out = []) {
  if (typeof value === "string") out.push([path, value]);
  else if (Array.isArray(value)) value.forEach((item, i) => leaves(item, [...path, i], out));
  else if (value && typeof value === "object") for (const [k, v] of Object.entries(value)) leaves(v, [...path, k], out);
  return out;
}

function getAt(node, path) {
  for (const seg of path) {
    if (node == null || typeof node !== "object") return undefined;
    node = node[seg];
  }
  return node;
}

/** Sets a value, creating arrays or objects to match the English shape. */
function setAt(node, path, value, english) {
  for (let i = 0; i < path.length - 1; i++) {
    const seg = path[i];
    if (node[seg] == null || typeof node[seg] !== "object") {
      node[seg] = Array.isArray(getAt(english, path.slice(0, i + 1))) ? [] : {};
    }
    node = node[seg];
  }
  node[path[path.length - 1]] = value;
}

/** Puts keys in English order, with a language's extra plural forms next to their English siblings. */
function ordered(english, translated) {
  if (Array.isArray(english)) {
    if (!Array.isArray(translated)) return undefined;
    // An item still missing stays an empty string (English shows for it), so later items keep their place.
    return english.map((item, i) => ordered(item, translated[i]) ?? (typeof item === "string" ? "" : undefined));
  }
  if (!english || typeof english !== "object") return translated;
  if (!translated || typeof translated !== "object") return undefined;
  const out = {};
  const bases = new Set();
  for (const key of Object.keys(english)) {
    const plural = PLURAL.exec(key);
    if (plural) {
      if (bases.has(plural[1])) continue;
      bases.add(plural[1]);
      for (const cat of CATEGORY_ORDER) {
        const k = `${plural[1]}_${cat}`;
        if (translated[k] !== undefined) out[k] = translated[k];
      }
      continue;
    }
    const value = ordered(english[key], translated[key]);
    if (value !== undefined) out[key] = value;
  }
  for (const [key, value] of Object.entries(translated)) if (!(key in out)) out[key] = value;
  return out;
}

const placeholders = (text) => [...String(text).matchAll(/\{(\w+)\}/g)].map((m) => m[1]).sort().join(",");
const words = (text) => String(text).split(/\s+/).filter(Boolean).length;

// ---- what to translate --------------------------------------------------------------------------------------------

const fileOf = (lang, ns) => join(NS_DIR[ns], `${lang}.json`);
const readJson = (file) => (existsSync(file) ? JSON.parse(readFileSync(file, "utf8")) : {});
const english = Object.fromEntries(NAMESPACES.map((ns) => [ns, readJson(fileOf("en", ns))]));

/** A translation that can stay: non-empty, with the same {placeholders} as English. */
const usable = (have, source) => typeof have === "string" && have.trim() !== "" && placeholders(have) === placeholders(source);

/** The strings a language still needs in one namespace: [{ path, source, note }]. */
function todo(lang, ns) {
  const current = readJson(fileOf(lang, ns));
  const categories = new Intl.PluralRules(lang).resolvedOptions().pluralCategories;
  const items = [];
  const seenPlural = new Set();
  for (const [path, text] of leaves(english[ns])) {
    const last = path[path.length - 1];
    const plural = typeof last === "string" ? PLURAL.exec(last) : null;
    if (!plural) {
      const have = getAt(current, path);
      if (ALL || !usable(have, text)) items.push({ path, source: text });
      continue;
    }
    const baseKey = path.slice(0, -1).concat(plural[1]).join("\u0000");
    if (seenPlural.has(baseKey)) continue;
    seenPlural.add(baseKey);
    const parent = path.slice(0, -1);
    const one = getAt(english[ns], [...parent, `${plural[1]}_one`]);
    const other = getAt(english[ns], [...parent, `${plural[1]}_other`]) ?? text;
    for (const cat of categories) {
      const keyPath = [...parent, `${plural[1]}_${cat}`];
      const have = getAt(current, keyPath);
      const source = cat === "one" && one ? one : other;
      if (!ALL && usable(have, source)) continue;
      items.push({
        path: keyPath,
        source,
        note: `Plural form "${cat}" (CLDR) of this message for the number in {count}; English singular: "${one ?? other}", English plural: "${other}". Write {count}, never a literal number.`,
      });
    }
  }
  return items;
}

// ---- the request --------------------------------------------------------------------------------------------------

const SEPARATOR = "\n§§\n";
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/** Swaps {name} for {0}, {1} … so the names are never translated; returns the way back. */
function protect(text) {
  const names = [];
  const out = text.replace(/\{(\w+)\}/g, (_, name) => {
    let i = names.indexOf(name);
    if (i < 0) i = names.push(name) - 1;
    return `{${i}}`;
  });
  return { out, back: (t) => t.replace(/\{\s*(\d+)\s*\}/g, (m, i) => (names[i] !== undefined ? `{${names[i]}}` : m)) };
}

/** Chrome's on-device Translator API, in a page served from memory (the API needs a secure origin). */
let chromePage = null;
let chromeQueue = Promise.resolve();
/** One call at a time: creating a translator needs the page's single user gesture. */
function chromeTranslate(lang, texts) {
  const result = chromeQueue.then(() =>
    chromeTranslateNow(lang, texts).catch((error) => {
      // Chrome was closed or crashed: open a new one for the next call.
      if (/has been closed|crashed/.test(error.message)) chromePage = null;
      throw error;
    }),
  );
  chromeQueue = result.catch(() => {});
  return result;
}
async function chromeTranslateNow(lang, texts) {
  if (!chromePage) {
    chromePage = (async () => {
      // Playwright isn't a dependency here: borrow pdf-editor's copy if this repo has none.
      const { chromium } = await import("@playwright/test").catch(() =>
        import(join(process.env.HOME, "Desktop", "pdf-editor", "node_modules", "@playwright", "test", "index.mjs")));
      const browser = await chromium.launch({ channel: "chrome", headless: false });
      process.on("exit", () => void browser.close());
      const page = await browser.newPage();
      await page.route("https://translate.local/**", (route) => route.fulfill({ contentType: "text/html", body: "<!doctype html><title>translate</title>" }));
      await page.goto("https://translate.local/");
      if (!(await page.evaluate(() => "Translator" in self))) throw new Error("this Chrome has no Translator API (needs Chrome 138 or later)");
      return page;
    })();
  }
  const page = await chromePage;
  // Creating a translator may download a language pack, which needs a fresh user gesture.
  const ready = await page.evaluate((lang) => Boolean(self.translators?.[lang]), lang);
  if (!ready) await page.mouse.click(5, 5);
  return page.evaluate(
    async ({ lang, texts }) => {
      self.translators ??= {};
      if (!self.translators[lang]) {
        const availability = await Translator.availability({ sourceLanguage: "en", targetLanguage: lang });
        if (availability === "unavailable") throw new Error(`Chrome cannot translate into ${lang}`);
        self.translators[lang] = Translator.create({ sourceLanguage: "en", targetLanguage: lang });
      }
      let translator;
      try {
        translator = await self.translators[lang];
      } catch (error) {
        delete self.translators[lang];
        throw error;
      }
      const out = [];
      for (const text of texts) out.push(await translator.translate(text));
      return out;
    },
    { lang, texts },
  );
}

/** One call to the endpoint translate.google.com itself uses; texts are joined with a separator line. */
async function google(lang, texts) {
  const body = new URLSearchParams({ q: texts.join(SEPARATOR) });
  const url = `https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=${GOOGLE_CODE[lang] ?? lang}&dt=t`;
  for (let attempt = 1; ; attempt++) {
    let res;
    try {
      res = await fetch(url, { method: "POST", body, signal: AbortSignal.timeout(30_000), headers: { "content-type": "application/x-www-form-urlencoded;charset=UTF-8" } });
    } catch (error) {
      if (attempt >= 6) throw error;
      await sleep(2000 * 2 ** attempt);
      continue;
    }
    if (res.url.includes("/sorry")) throw Object.assign(new Error("Google is asking for a captcha (too many requests)"), { blocked: true });
    if (res.ok) {
      const data = await res.json();
      const joined = data[0].map((part) => part[0]).join("");
      return joined.split(/\n?\s*§§\s*\n?/).map((t) => t.trim());
    }
    if (attempt >= 6 || (res.status !== 429 && res.status < 500)) throw new Error(`Google Translate answered ${res.status}`);
    await sleep(2000 * 2 ** attempt);
  }
}

async function translate(lang, ns, items) {
  const prepared = items.map((item) => protect(item.source));
  let result;
  if (ENGINE === "google") {
    try {
      result = await google(lang.code, prepared.map((p) => p.out));
    } catch (error) {
      if (!error.blocked) throw error;
      if (ENGINE === "google") console.log("Google is blocking requests from this network for now; switching to Chrome's on-device translator.");
      ENGINE = "chrome";
    }
  }
  if (ENGINE === "chrome") result = await chromeTranslate(GOOGLE_CODE[lang.code] ?? lang.code, prepared.map((p) => p.out));
  const good = [];
  const bad = [];
  if (result.length !== items.length) {
    // The separator got lost somewhere: send these one by one instead.
    if (items.length === 1) return { good, bad: items };
    for (const item of items) {
      const one = await translate(lang, ns, [item]);
      good.push(...one.good);
      bad.push(...one.bad);
    }
    return { good, bad };
  }
  items.forEach((item, i) => {
    const value = prepared[i].back(result[i]);
    if (value && placeholders(value) === placeholders(item.source)) good.push({ ...item, value });
    else bad.push(item);
  });
  return { good, bad };
}

/** Writes finished strings into the language file (re-read first, so chunks of one file never overwrite each other). */
function save(lang, ns, good) {
  const file = fileOf(lang, ns);
  const current = readJson(file);
  for (const item of good) setAt(current, item.path, item.value, english[ns]);
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(`${file}.tmp`, `${JSON.stringify(ordered(english[ns], current), null, 2)}\n`);
  renameSync(`${file}.tmp`, file);
}

// ---- run ----------------------------------------------------------------------------------------------------------

const jobs = [];
for (const lang of languages) {
  for (const ns of NAMESPACES) {
    const items = todo(lang.code, ns);
    let chunk = [];
    let size = 0;
    for (const item of items) {
      if (chunk.length && size + item.source.length > CHUNK_CHARS) {
        jobs.push({ lang, ns, items: chunk });
        chunk = [];
        size = 0;
      }
      chunk.push(item);
      size += item.source.length + SEPARATOR.length;
    }
    if (chunk.length) jobs.push({ lang, ns, items: chunk });
  }
}

const totalStrings = jobs.reduce((n, j) => n + j.items.length, 0);
const totalWords = jobs.reduce((n, j) => n + j.items.reduce((w, it) => w + words(it.source), 0), 0);
console.log(`${totalStrings} strings (${totalWords} English words) in ${jobs.length} requests for ${languages.length} language(s).`);
if (DRY) {
  const perLang = new Map();
  for (const j of jobs) perLang.set(j.lang.code, (perLang.get(j.lang.code) ?? 0) + j.items.length);
  for (const [code, n] of perLang) console.log(`  ${code}: ${n} strings`);
  process.exit(0);
}
if (jobs.length === 0) {
  console.log("Nothing to translate.");
  process.exit(0);
}

const failed = [];
let done = 0;
let next = 0;

async function run(job, attempt = 1) {
  const label = `${job.lang.code}/${job.ns} (${job.items.length})`;
  try {
    const { good, bad } = await translate(job.lang, job.ns, job.items);
    if (good.length) save(job.lang.code, job.ns, good);
    done += good.length;
    if (bad.length && attempt === 1) {
      await run({ ...job, items: bad }, 2);
      return;
    }
    for (const item of bad) failed.push(`${job.lang.code}/${job.ns}: ${item.path.join(".")}`);
    console.log(`✓ ${label}  ${done}/${totalStrings}`);
  } catch (error) {
    if (attempt === 1) {
      console.log(`… ${label}: ${error.message}, retrying`);
      await sleep(5000);
      await run(job, 2);
    } else {
      console.log(`✗ ${label}: ${error.message}`);
      for (const item of job.items) failed.push(`${job.lang.code}/${job.ns}: ${item.path.join(".")} (${error.message})`);
    }
  }
}

await Promise.all(
  Array.from({ length: Math.min(CONCURRENCY, jobs.length) }, async () => {
    while (next < jobs.length) await run(jobs[next++]);
  }),
);

console.log(`\nTranslated ${done} of ${totalStrings} strings.`);
if (failed.length) {
  console.log(`${failed.length} string(s) were not written (English shows for them); run again to retry:`);
  for (const line of failed.slice(0, 50)) console.log(`  ${line}`);
  if (failed.length > 50) console.log(`  … and ${failed.length - 50} more`);
}
console.log("Next: node scripts/build-site.mjs, then the app's I18nTest.");
process.exit(0);
