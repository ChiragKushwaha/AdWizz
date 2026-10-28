package rocks.chirag.adwizz.focus

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortVideoRulesTest {
    @Test fun spotsShortVideoAddresses() {
        listOf(
            "youtube.com/shorts/abc123",
            "https://m.youtube.com/shorts/abc123?feature=share",
            "www.instagram.com/reels/xyz/",
            "instagram.com/reel/xyz",
            "m.facebook.com/reel/123",
            "https://www.tiktok.com/@someone/video/1",
            "vm.tiktok.com/ZM123",
            "tiktok.com",
            "snapchat.com/spotlight/abc",
        ).forEach { assertTrue(it, ShortVideoRules.isShortVideoUrl(it)) }
    }

    @Test fun leavesNormalPagesAlone() {
        listOf(
            "",
            "youtube.com",
            "m.youtube.com/watch?v=abc",
            "youtube.com/results?search_query=shorts",
            "instagram.com/someone",
            "facebook.com",
            "nottiktok.com",
            "google.com/search?q=tiktok.com",
            "tiktok shorts",     // a search being typed
        ).forEach { assertFalse(it, ShortVideoRules.isShortVideoUrl(it)) }
    }

    @Test fun accessibilityConfigListsEveryWatchedPackage() {
        val xml = File("src/main/res/xml/short_video_blocker.xml").readText()
        val listed = Regex("android:packageNames=\"([^\"]*)\"").find(xml)!!.groupValues[1].split(',').toSet()
        val watched = ShortVideoRules.SCREEN_IDS.keys + ShortVideoRules.WHOLE_APPS + ShortVideoRules.BROWSER_URL_IDS.keys
        assertEquals(watched, listed)
    }
}
