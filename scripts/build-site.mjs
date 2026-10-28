// Builds the website in every language: docs/index.html (English) and docs/<lang>/index.html, plus docs/sitemap.xml.
//
//   node scripts/build-site.mjs
//
// Text lives in site/i18n/<lang>.json (English in en.json; fill the others with `node scripts/translate.mjs`),
// the page in site/index.template.html. Strings may use {b}…{eb} (bold), {i}…{ei} (italic) and link tokens; the
// translator keeps them as they are, like {placeholders}. Each page gets hreflang links to all the others, its own
// canonical URL, og:locale and translated structured data (app + FAQ), so search engines list the right language.

import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const SITE = "https://chiragkushwaha.github.io/AdWizz/";
const REPO = "https://github.com/ChiragKushwaha/AdWizz";
const APK = `${REPO}/releases/latest/download/AdWizz.apk`;

const languages = JSON.parse(readFileSync(join(root, "i18n", "languages.json"), "utf8"));
const template = readFileSync(join(root, "site", "index.template.html"), "utf8");
const read = (code) => {
  const f = join(root, "site", "i18n", `${code}.json`);
  return existsSync(f) ? JSON.parse(readFileSync(f, "utf8")) : {};
};
const english = read("en");
const update = JSON.parse(readFileSync(join(root, "docs", "update.json"), "utf8"));

/** hreflang / BCP 47 tag for one of our codes. */
const tag = (code) => ({ zh: "zh-Hans" })[code] ?? code;
const urlOf = (code) => (code === "en" ? SITE : `${SITE}${code}/`);
const escape = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");

const TOKENS = {
  b: "<strong>", eb: "</strong>", i: "<i>", ei: "</i>", ea: "</a>",
  a: `<a href="${APK}">`,
  a1: `<a href="${REPO}/blob/main/LICENSE">`,
  a2: `<a href="${REPO}">`,
  a3: `<a href="${REPO}/issues">`,
};
/** Page text: escaped, with formatting tokens turned into tags. */
const html = (s) => escape(s).replace(/\{(\w+)\}/g, (m, t) => TOKENS[t] ?? m);
/** Plain text (attributes, structured data): tokens dropped. */
const plain = (s) => String(s).replace(/\{(\w+)\}/g, (m, t) => (t in TOKENS ? "" : m));

const missing = new Map();
const pages = [];
for (const lang of languages) {
  const own = read(lang.code);
  const t = (key) => {
    if (own[key]) return own[key];
    if (lang.code !== "en") missing.set(lang.code, (missing.get(lang.code) ?? 0) + 1);
    if (!(key in english)) throw new Error(`site/i18n/en.json has no "${key}"`);
    return english[key];
  };
  const base = lang.code === "en" ? "" : "../";
  const faqKeys = ["play", "notinstalled", "vpn", "youtube", "gap", "reels", "browser", "hotspot"];
  const jsonld = [
    {
      "@context": "https://schema.org", "@type": "SoftwareApplication", name: "AdWizz",
      description: plain(t("meta.description")), inLanguage: tag(lang.code),
      applicationCategory: "UtilitiesApplication", applicationSubCategory: "Ad blocker",
      operatingSystem: "Android 7.0 or later", url: urlOf(lang.code), downloadUrl: APK, installUrl: APK,
      fileSize: "2 MB", softwareVersion: update.versionName, image: `${SITE}img/og.png`,
      screenshot: ["home-light", "spell", "home-dark", "settings", "focus", "devices"].map((s) => `${SITE}screenshots/${s}.webp`),
      offers: { "@type": "Offer", price: "0", priceCurrency: "USD" },
      author: { "@type": "Person", name: "Chirag Kushwaha", url: "https://github.com/ChiragKushwaha" },
      license: `${REPO}/blob/main/LICENSE`, isAccessibleForFree: true, codeRepository: REPO,
      keywords: plain(t("meta.keywords")),
    },
    {
      "@context": "https://schema.org", "@type": "FAQPage", inLanguage: tag(lang.code),
      mainEntity: faqKeys.map((k) => ({
        "@type": "Question", name: plain(t(`faq.${k}.q`)),
        acceptedAnswer: { "@type": "Answer", text: plain(t(`faq.${k}.a`)) },
      })),
    },
    { "@context": "https://schema.org", "@type": "WebSite", name: "AdWizz", url: urlOf(lang.code), inLanguage: tag(lang.code) },
  ];
  const vars = {
    lang: tag(lang.code), dir: lang.dir, base, url: urlOf(lang.code), home: urlOf(lang.code), og_locale: lang.og, qr_suffix: "",
    hreflang: [
      ...languages.map((l) => `  <link rel="alternate" hreflang="${tag(l.code)}" href="${urlOf(l.code)}">`),
      `  <link rel="alternate" hreflang="x-default" href="${SITE}">`,
    ].join("\n"),
    og_alternates: languages.filter((l) => l.code !== lang.code).map((l) => `  <meta property="og:locale:alternate" content="${l.og}">`).join("\n"),
    lang_options: languages.map((l) => `      <option value="${urlOf(l.code)}" lang="${tag(l.code)}"${l.code === lang.code ? " selected" : ""}>${escape(l.name)}</option>`).join("\n"),
    // "<" escaped so text can never close the script tag.
    jsonld: JSON.stringify(jsonld).replace(/</g, "\\u003c"),
    js_strings: JSON.stringify({ toLight: plain(t("theme.to_light")), toDark: plain(t("theme.to_dark")), live: plain(t("hero.meta_live")) }).replace(/</g, "\\u003c"),
  };
  const ATTR = /^(meta\.|og\.|cta\.download_aria|wizard\.alt|qr\.alt|shots\.aria|shot\d\.alt|theme\.|lang\.label)/;
  const page = template.replace(/\{\{([a-z0-9_.]+)\}\}/g, (m, key) => {
    if (key in vars) return vars[key];
    return ATTR.test(key) ? escape(plain(t(key))) : html(t(key));
  });
  const out = lang.code === "en" ? join(root, "docs", "index.html") : join(root, "docs", lang.code, "index.html");
  mkdirSync(dirname(out), { recursive: true });
  writeFileSync(out, page);
  pages.push(lang.code);
}

// Sitemap: every page, each listing all its language versions (Google's recommended form for multilingual sites).
const today = new Date().toISOString().slice(0, 10);
const alternates = [
  ...languages.map((l) => `    <xhtml:link rel="alternate" hreflang="${tag(l.code)}" href="${urlOf(l.code)}"/>`),
  `    <xhtml:link rel="alternate" hreflang="x-default" href="${SITE}"/>`,
].join("\n");
const images = ["img/og.png", ...["home-light", "spell", "home-dark", "settings", "focus", "devices"].map((s) => `screenshots/${s}.webp`)]
  .map((p) => `    <image:image><image:loc>${SITE}${p}</image:loc></image:image>`).join("\n");
const sitemap = `<?xml version="1.0" encoding="UTF-8"?>
<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9" xmlns:xhtml="http://www.w3.org/1999/xhtml" xmlns:image="http://www.google.com/schemas/sitemap-image/1.1">
${languages.map((l) => `  <url>
    <loc>${urlOf(l.code)}</loc>
    <lastmod>${today}</lastmod>
    <changefreq>weekly</changefreq>
    <priority>${l.code === "en" ? "1.0" : "0.8"}</priority>
${alternates}
${images}
  </url>`).join("\n")}
</urlset>
`;
writeFileSync(join(root, "docs", "sitemap.xml"), sitemap);

console.log(`Built ${pages.length} pages and sitemap.xml.`);
if (missing.size) {
  console.log("Untranslated strings (English shown): " + [...missing].map(([c, n]) => `${c} ${n}`).join(", "));
}
