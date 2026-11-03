package rocks.chirag.adwizz.focus

import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Muting for ads never leaves the phone muted, and never unmutes what the user muted. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class AdMuterTest {
    private val audio = ApplicationProvider.getApplicationContext<Context>().getSystemService(AudioManager::class.java)
    private val muter = AdMuter(audio)
    private fun muted() = audio.isStreamMute(AudioManager.STREAM_MUSIC)

    @Test fun mutesUntilEveryAdIsOver() {
        muter.set(AdMuter.Source.YOUTUBE, true)
        muter.set(AdMuter.Source.SPOTIFY, true)
        assertTrue(muted())
        muter.set(AdMuter.Source.YOUTUBE, false)
        assertTrue(muted())
        muter.set(AdMuter.Source.SPOTIFY, false)
        assertFalse(muted())
    }

    @Test fun leavesTheUsersOwnMuteAlone() {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        muter.set(AdMuter.Source.YOUTUBE, true)
        muter.set(AdMuter.Source.YOUTUBE, false)
        assertTrue(muted())
    }

    @Test fun releaseAllUnmutes() {
        muter.set(AdMuter.Source.SPOTIFY, true)
        muter.releaseAll()
        assertFalse(muted())
        assertFalse(muter.isMuted)
    }
}
