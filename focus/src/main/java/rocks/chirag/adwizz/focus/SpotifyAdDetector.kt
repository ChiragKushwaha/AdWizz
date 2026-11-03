package rocks.chirag.adwizz.focus

/**
 * Works out when Spotify is playing an ad from the broadcasts it sends when "Device broadcast
 * status" is on in Spotify's settings. Spotify reports every song but stays quiet about most ads,
 * so: when a song should have ended and no new song was announced while still playing, it's an ad.
 * An ad ID ("spotify:ad:…") counts straight away. A new song, a pause or [MAX_AD_MS] ends it.
 *
 * Plain logic: [schedule] runs a check later, [onAd] mutes or unmutes. Times are in ms.
 */
internal class SpotifyAdDetector(
    private val schedule: (delayMs: Long, check: () -> Unit) -> Unit,
    private val cancel: () -> Unit,
    private val onAd: (Boolean) -> Unit,
) {
    private var trackId: String? = null
    private var lengthMs = 0L
    private var playing = false
    private var inAd = false
    /** Bumped on every broadcast so a check scheduled before it is ignored. */
    private var generation = 0

    fun onMetadata(id: String?, length: Long, now: Long) {
        generation++
        cancel()
        if (AdRules.isSpotifyAdId(id)) { setAd(true); scheduleAdTimeout(); return }
        trackId = id
        lengthMs = AdRules.spotifyLengthMs(length)
        setAd(false)
        if (playing) scheduleEnd(0, now, now)
    }

    fun onPlayback(isPlaying: Boolean, positionMs: Long, timeSent: Long, now: Long) {
        generation++
        cancel()
        playing = isPlaying
        if (!isPlaying) { setAd(false); return }
        if (inAd) { scheduleAdTimeout(); return }
        scheduleEnd(positionMs, timeSent, now)
    }

    fun stop() { generation++; cancel(); setAd(false) }

    private fun scheduleEnd(positionMs: Long, timeSent: Long, now: Long) {
        if (lengthMs <= 0 || trackId == null) return
        val endsIn = (timeSent + lengthMs - positionMs) - now
        val gen = generation
        schedule((endsIn + GRACE_MS).coerceAtLeast(GRACE_MS)) {
            // Still the same song and still playing past its end: Spotify is playing an ad.
            if (gen == generation && playing) { setAd(true); scheduleAdTimeout() }
        }
    }

    private fun scheduleAdTimeout() {
        val gen = generation
        schedule(MAX_AD_MS) { if (gen == generation) setAd(false) }
    }

    private fun setAd(ad: Boolean) {
        if (ad == inAd) return
        inAd = ad
        onAd(ad)
    }

    companion object {
        /** Songs often run a moment past their length; don't mute the last note. */
        const val GRACE_MS = 2_500L
        /** Never stay muted longer than an ad break, even if Spotify goes quiet. */
        const val MAX_AD_MS = 90_000L
    }
}
