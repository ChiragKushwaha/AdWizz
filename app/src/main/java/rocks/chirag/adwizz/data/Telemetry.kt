package rocks.chirag.adwizz.data

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance

/**
 * One place for Firebase: Analytics events (shown live in the Realtime / DebugView reports),
 * Crashlytics context and Performance traces. Release Monitoring in the console is built from
 * Crashlytics + Analytics, so it needs nothing extra here.
 *
 * Privacy: never pass domain names, app names or anything the user browsed — only counts,
 * states and versions. Every call is best-effort: telemetry must never break the app (and in
 * unit tests Firebase isn't initialised at all).
 */
object Telemetry {
    private const val TAG = "AdWizzTelemetry"

    /** Analytics event, e.g. event(context, "spell_cast", "list_size" to 791_000). */
    fun event(context: Context, name: String, vararg params: Pair<String, Any>) = safely {
        val bundle = Bundle().apply {
            params.forEach { (k, v) ->
                when (v) {
                    is Long -> putLong(k, v)
                    is Int -> putLong(k, v.toLong())
                    is Boolean -> putLong(k, if (v) 1 else 0)
                    else -> putString(k, v.toString().take(100))
                }
            }
        }
        FirebaseAnalytics.getInstance(context).logEvent(name, bundle)
    }

    /** Context attached to any later crash report (e.g. was protection on, how many domains). */
    fun crashContext(key: String, value: String) = safely { FirebaseCrashlytics.getInstance().setCustomKey(key, value) }

    /** Something went wrong but the app recovered: report it without crashing. */
    fun nonFatal(error: Throwable) = safely { FirebaseCrashlytics.getInstance().recordException(error) }

    /** Times [block] as a Performance Monitoring custom trace named [name]. */
    inline fun <T> trace(name: String, block: () -> T): T {
        val trace = runCatching { FirebasePerformance.getInstance().newTrace(name).also { it.start() } }.getOrNull()
        try {
            return block()
        } finally {
            runCatching { trace?.stop() }
        }
    }

    private inline fun safely(block: () -> Unit) {
        try { block() } catch (e: Throwable) { Log.d(TAG, "Telemetry skipped: ${e.message}") }
    }
}
