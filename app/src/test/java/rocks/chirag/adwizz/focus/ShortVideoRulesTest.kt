package rocks.chirag.adwizz.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ShortVideoRulesTest {
    @Test fun viewIdsAreFullyQualified() {
        assertEquals(
            "com.google.android.youtube:id/reel_recycler",
            ShortVideoRules.viewIdsFor("com.google.android.youtube").first()
        )
        assertTrue(ShortVideoRules.viewIdsFor("com.example.unwatched").isEmpty())
    }

    /** The service only hears from apps listed in its XML config, so it must cover every rule. */
    @Test fun serviceConfigWatchesEveryRuledApp() {
        val xml = File("src/main/res/xml/short_video_blocker.xml").readText()
        val listed = Regex("""android:packageNames="([^"]+)"""").find(xml)!!.groupValues[1].split(',').toSet()
        assertEquals(ShortVideoRules.SCREEN_IDS.keys + ShortVideoRules.WHOLE_APPS, listed)
    }
}
