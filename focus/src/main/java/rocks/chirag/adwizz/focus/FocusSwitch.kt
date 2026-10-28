package rocks.chirag.adwizz.focus

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * The "Block Reels & Shorts" switch, read from AdWizz's FocusStateProvider and kept current as the
 * user flips it. Off whenever AdWizz is missing or not signed with our key (the provider is
 * guarded by a signature-only permission).
 */
internal class FocusSwitch(private val context: Context) {
    @Volatile var on = false
        private set

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    fun start() {
        runCatching { context.contentResolver.registerContentObserver(URI, false, observer) }
            .onFailure { Log.w(TAG, "Can't watch AdWizz's switch", it) }
        refresh()
    }

    fun stop() {
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }

    fun refresh() {
        on = runCatching {
            context.contentResolver.query(URI, null, null, null, null)?.use { c ->
                c.moveToFirst() && c.getInt(0) == 1
            } ?: false
        }.onFailure { Log.w(TAG, "Can't read AdWizz's switch", it) }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "AdWizzFocus"
        val URI: Uri = Uri.parse("content://${BuildConfig.MAIN_APP_ID}.focusstate/switch")
    }
}
