package rocks.chirag.adwizz.focus

import android.media.AudioManager

/**
 * Mutes media while an ad plays and unmutes when every ad is over. Only ever unmutes what it muted
 * itself, so a phone the user muted stays muted.
 */
internal class AdMuter(private val audio: AudioManager?) {
    enum class Source { YOUTUBE, SPOTIFY }

    private val active = HashSet<Source>()
    private var mutedByUs = false

    val isMuted: Boolean get() = mutedByUs

    @Synchronized fun set(source: Source, ad: Boolean) {
        val changed = if (ad) active.add(source) else active.remove(source)
        if (changed && BuildConfig.DEBUG) android.util.Log.d("AdWizzAds", "mute $source=$ad")
        val a = audio ?: return
        if (active.isNotEmpty() && !mutedByUs) {
            if (a.isStreamMute(AudioManager.STREAM_MUSIC)) return   // already silent: leave it to the user
            a.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
            mutedByUs = true
        } else if (active.isEmpty() && mutedByUs) {
            a.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            mutedByUs = false
        }
    }

    /** Unmutes no matter which ads are still on (pausing, switching off, shutting down). */
    @Synchronized fun releaseAll() {
        active.clear()
        set(Source.YOUTUBE, false)
    }
}
