package rocks.chirag.adwizz.focus

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * The add-on's switches, read from AdWizz's FocusStateProvider and kept current as the user flips
 * them. Everything is off whenever AdWizz is missing or not signed with our key (the provider is
 * guarded by a signature-only permission). [onChange] runs on the main thread after each change.
 */
internal class FocusSwitch(private val context: Context, private val onChange: () -> Unit = {}) {
    /** "Block Reels & Shorts". */
    @Volatile var on = false
        private set
    @Volatile var skipYoutubeAds = false
        private set
    @Volatile var muteSpotifyAds = false
        private set
    /** "Block sponsored content": cover sponsored cards in YouTube and Instagram. */
    @Volatile var hideSponsored = false
        private set
    /** The add-on does nothing until this time (ms since 1970). */
    @Volatile var pausedUntil = 0L
        private set
    /** When AdWizz last asked the add-on to switch itself off (ms), 0 if never. */
    @Volatile var offRequest = 0L
        private set
    /** False when AdWizz couldn't be read at all (uninstalled, or signed by someone else). */
    @Volatile var reachable = false
        private set

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh(); this@FocusSwitch.onChange() }
    }

    fun start() {
        runCatching { context.contentResolver.registerContentObserver(URI, false, observer) }
            .onFailure { Log.w(TAG, "Can't watch AdWizz's switch", it) }
        refresh()
    }

    fun stop() {
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
    }

    fun isPaused(now: Long = System.currentTimeMillis()) = pausedUntil > now

    /** True when some feature is on and not paused: the service should be watching. */
    fun active(now: Long = System.currentTimeMillis()) = (on || skipYoutubeAds || muteSpotifyAds || hideSponsored) && !isPaused(now)

    fun refresh() {
        val read = runCatching {
            context.contentResolver.query(URI, null, null, null, null)?.use { c -> if (c.moveToFirst()) Values.from(c) else null }
        }.onFailure { Log.w(TAG, "Can't read AdWizz's switch", it) }.getOrNull()
        reachable = read != null
        val v = read ?: Values()
        on = v.reels; skipYoutubeAds = v.skipYoutube; muteSpotifyAds = v.muteSpotify; pausedUntil = v.pausedUntil; offRequest = v.offRequest
        hideSponsored = v.hideSponsored
    }

    private data class Values(
        val reels: Boolean = false,
        val skipYoutube: Boolean = false,
        val muteSpotify: Boolean = false,
        val pausedUntil: Long = 0,
        val offRequest: Long = 0,
        val hideSponsored: Boolean = false,
    ) {
        companion object {
            /** AdWizz 1.4 and older only send the first column; the rest are then off. */
            fun from(c: Cursor): Values {
                fun long(name: String) = c.getColumnIndex(name).takeIf { it >= 0 }?.let { c.getLong(it) } ?: 0L
                return Values(c.getInt(0) == 1, long("skip_youtube_ads") == 1L, long("mute_spotify_ads") == 1L,
                    long("paused_until"), long("off_request"), long("hide_sponsored") == 1L)
            }
        }
    }

    companion object {
        private const val TAG = "AdWizzFocus"
        val URI: Uri = Uri.parse("content://${BuildConfig.MAIN_APP_ID}.focusstate/switch")
    }
}
