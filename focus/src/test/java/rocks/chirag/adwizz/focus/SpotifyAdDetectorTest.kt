package rocks.chirag.adwizz.focus

import org.junit.Assert.*
import org.junit.Test

/** Spotify ads: muted when a song overruns without a new one announced, unmuted by the next song. */
class SpotifyAdDetectorTest {
    private var pending: (() -> Unit)? = null
    private var pendingDelay = 0L
    private val events = mutableListOf<Boolean>()
    private val d = SpotifyAdDetector(
        schedule = { delay, run -> pending = run; pendingDelay = delay },
        cancel = { pending = null },
        onAd = { events += it },
    )

    private fun fire() { val p = pending; pending = null; p?.invoke() }

    @Test fun songThatOverrunsIsAnAd() {
        d.onMetadata("spotify:track:1", 180, now = 0)
        d.onPlayback(true, positionMs = 0, timeSent = 0, now = 0)
        assertEquals(180_000 + SpotifyAdDetector.GRACE_MS, pendingDelay)
        fire()
        assertEquals(listOf(true), events)
        d.onMetadata("spotify:track:2", 200, now = 215_000)   // next song: unmute
        assertEquals(listOf(true, false), events)
    }

    @Test fun nextSongBeforeTheEndMeansNoAd() {
        d.onMetadata("spotify:track:1", 180, now = 0)
        d.onPlayback(true, 0, 0, 0)
        d.onMetadata("spotify:track:2", 200, now = 181_000)
        d.onPlayback(true, 0, 181_000, 181_000)
        assertTrue(events.isEmpty())
    }

    @Test fun pauseNeverMutes() {
        d.onMetadata("spotify:track:1", 180, now = 0)
        d.onPlayback(true, 0, 0, 0)
        d.onPlayback(false, 30_000, 30_000, 30_000)
        assertNull(pending)
        assertTrue(events.isEmpty())
    }

    @Test fun adIdsMuteRightAwayAndTimeOut() {
        d.onMetadata("spotify:ad:abc", 30, now = 0)
        assertEquals(listOf(true), events)
        assertEquals(SpotifyAdDetector.MAX_AD_MS, pendingDelay)
        fire()
        assertEquals(listOf(true, false), events)
    }

    @Test fun seekingReschedulesFromTheNewPosition() {
        d.onMetadata("spotify:track:1", 180, now = 0)
        d.onPlayback(true, positionMs = 170_000, timeSent = 10_000, now = 10_000)
        assertEquals(10_000 + SpotifyAdDetector.GRACE_MS, pendingDelay)
    }

    @Test fun lengthInMillisecondsToo() {
        assertEquals(180_000L, AdRules.spotifyLengthMs(180))
        assertEquals(180_000L, AdRules.spotifyLengthMs(180_000))
        assertTrue(AdRules.isSpotifyAdId("spotify:ad:1"))
        assertFalse(AdRules.isSpotifyAdId("spotify:track:1"))
    }
}
