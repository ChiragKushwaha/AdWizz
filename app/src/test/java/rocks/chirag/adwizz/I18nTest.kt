package rocks.chirag.adwizz

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.util.Locale
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Translations: every key exists in English, every language keeps the placeholders, fallbacks work. */
@RunWith(AndroidJUnit4::class)   // org.json, assets
@Config(sdk = [35])
class I18nTest {
    private val dir = File("src/main/assets/i18n")
    private fun read(code: String): Map<String, String> {
        val json = JSONObject(File(dir, "$code.json").readText())
        return json.keys().asSequence().associateWith { json.getString(it) }
    }
    private val english = read("en")
    private fun placeholders(s: String) = Regex("\\{(\\w+)\\}").findAll(s).map { it.groupValues[1] }.toSortedSet()

    @Test fun everyKeyUsedInTheCodeIsInEnglish() {
        val used = File("src/main/java").walk().filter { it.extension == "kt" }
            .flatMap { f -> Regex("""\btr\("([a-z0-9_.]+)"""").findAll(f.readText()).map { it.groupValues[1] } }
            .toSet()
        assertTrue(used.size > 150)
        assertEquals(emptySet<String>(), used - english.keys)
    }

    @Test fun everyBlocklistHasADescription() {
        rocks.chirag.adwizz.data.Sources.ALL.forEach { assertTrue(it.id, "src.${it.id}" in english) }
    }

    @Test fun translationsKeepThePlaceholders() {
        val problems = I18n.LANGUAGES.filter { it != "en" && File(dir, "$it.json").exists() }.flatMap { code ->
            read(code).filter { (k, v) -> k in english && placeholders(v) != placeholders(english.getValue(k)) }
                .map { (k, _) -> "$code: $k" }
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test fun websiteTranslationsKeepTokensAndPlaceholders() {
        val site = File("../site/i18n")
        fun load(f: File) = JSONObject(f.readText()).let { j -> j.keys().asSequence().associateWith { j.getString(it) } }
        val en = load(File(site, "en.json"))
        val problems = site.listFiles()!!.filter { it.name != "en.json" }.flatMap { f ->
            load(f).filter { (k, v) -> k in en && placeholders(v) != placeholders(en.getValue(k)) }.map { (k, _) -> "${f.name}: $k" }
        }
        assertEquals(emptyList<String>(), problems)
    }

    @Test fun appAndWebsiteShareOneLanguageList() {
        val json = File("../i18n/languages.json").readText()
        val codes = Regex("\"code\": \"([a-z]+)\"").findAll(json).map { it.groupValues[1] }.toList()
        assertEquals(I18n.LANGUAGES, codes)
    }

    @Test fun everyLanguageHasAFileAndIsOfferedToAndroid() {
        val config = File("src/main/res/xml/locales_config.xml").readText()
        I18n.LANGUAGES.forEach { code ->
            assertTrue("missing assets/i18n/$code.json", File(dir, "$code.json").exists())
            val tag = mapOf("zh" to "zh-Hans").getOrDefault(code, code)
            assertTrue("$tag not in locales_config.xml", config.contains("android:name=\"$tag\""))
        }
        assertTrue(I18n.LANGUAGES.size >= 40)
    }

    @Test fun androidsOldLanguageCodesAreMapped() {
        assertEquals("id", I18n.codeFor(Locale("in", "ID")))
        assertEquals("he", I18n.codeFor(Locale("iw", "IL")))
        assertEquals("fil", I18n.codeFor(Locale("tl", "PH")))
        assertEquals("hi", I18n.codeFor(Locale("hi", "IN")))
        assertEquals("zh", I18n.codeFor(Locale.SIMPLIFIED_CHINESE))
        assertEquals("en", I18n.codeFor(Locale("xx")))      // a language we don't have
    }

    @Test fun fillsPlaceholdersAndFallsBackToEnglish() {
        assertEquals("Updated 3 list(s)", tr("lists.result_ok", "count" to 3))
        assertEquals("Protected", tr("home.protected"))
        assertEquals("no.such.key", tr("no.such.key"))
    }
}
