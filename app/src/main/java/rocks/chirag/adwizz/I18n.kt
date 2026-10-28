package rocks.chirag.adwizz

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import org.json.JSONObject
import java.util.Locale

/**
 * AdWizz's translations: assets/i18n/<lang>.json, one flat {"key": "text"} file per language,
 * with {name} placeholders. Kept as compressed assets rather than Android string resources
 * (resources.arsc is stored uncompressed), so 40 languages add ~200 KB instead of ~800 KB.
 *
 * Follows the phone's language, or the one picked for AdWizz in Android 13+'s per-app language
 * setting. Anything a language is missing falls back to English.
 */
object I18n {
    @Volatile private var app: Context? = null
    @Volatile private var lang = "en"
    @Volatile private var table: Map<String, String> = emptyMap()
    @Volatile private var english: Map<String, String> = emptyMap()

    /** Languages with a translation file; keep in step with res/xml/locales_config.xml. */
    val LANGUAGES = listOf(
        "en", "hi", "bn", "es", "fr", "ar", "pt", "ru", "ur", "id", "de", "ja", "zh", "tr", "vi", "ko", "it",
        "ta", "te", "mr", "pa", "gu", "kn", "ml", "or", "ne", "fa", "sw", "th", "fil", "ms", "pl", "nl", "uk",
        "ro", "el", "cs", "hu", "sv", "he",
    )

    fun init(context: Context) {
        app = context.applicationContext
        reload()
    }

    /** Re-reads the language (call when the configuration changes). */
    fun reload() {
        val ctx = app ?: return
        if (english.isEmpty()) english = load(ctx, "en")
        lang = codeFor(ctx.resources.configuration.locales[0])
        table = if (lang == "en") english else load(ctx, lang)
    }

    /** The language AdWizz is showing, e.g. "hi". */
    val current: String get() = lang

    fun t(key: String, args: Array<out Pair<String, Any?>>): String {
        var s = table[key] ?: english[key] ?: key
        for ((name, value) in args) s = s.replace("{$name}", value.toString())
        return s
    }

    /** Our file name for a locale (Android still reports some old codes: "in" for Indonesian, "iw" for Hebrew). */
    fun codeFor(locale: Locale): String {
        val code = when (val l = locale.language) {
            "in" -> "id"; "iw" -> "he"; "tl" -> "fil"
            else -> l
        }
        return if (code in LANGUAGES) code else "en"
    }

    private fun load(ctx: Context, code: String): Map<String, String> = runCatching {
        val json = JSONObject(ctx.assets.open("i18n/$code.json").bufferedReader().use { it.readText() })
        buildMap { json.keys().forEach { put(it, json.getString(it)) } }
    }.getOrDefault(emptyMap())
}

/** Translated text for [key], e.g. tr("home.protected") or tr("update.banner", "version" to "1.3"). */
fun tr(key: String, vararg args: Pair<String, Any?>): String = I18n.t(key, args)

/** Loads the translations before any screen, notification or service needs them. */
class AdWizzApp : Application() {
    override fun onCreate() {
        super.onCreate()
        I18n.init(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        I18n.reload()
    }
}
