package rocks.chirag.adwizz.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rocks.chirag.adwizz.data.BlockCategory
import rocks.chirag.adwizz.data.BlockStats

/** The Stats tab: tiles, per-day chart and per-app bars from what the VPN recorded. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp")
class StatsTabTest : UiTestBase() {
    @Before fun stats() { BlockStats.resetForTest(); BlockStats.init(ctx) }
    @After fun clear() = BlockStats.resetForTest()

    @Test fun emptyStateExplainsWhatAppears() {
        launchApp()
        openTab("tab_stats")
        rule.onNodeWithText("No numbers yet", substring = true).assertExists()
    }

    @Test fun showsChartsPerDayAndPerApp() {
        val now = System.currentTimeMillis()
        BlockStats.record("com.example.game", "ads.example.com", BlockCategory.ADS, now)
        BlockStats.record("com.example.game", "pixel.example.com", BlockCategory.TRACKERS, now)
        BlockStats.record("com.example.news", "ads2.example.com", BlockCategory.ADS, now)
        launchApp()
        openTab("tab_stats")
        rule.onNodeWithTag("dailyChart").assertExists()
        scrollToTag("statsList", "chart_ADS").assertExists()
        scrollTo("statsList", "Trackers blocked by app").assertExists()
        rule.onNodeWithTag("range_1").performClick()
        rule.onNodeWithTag("dailyChart").assertDoesNotExist()
        rule.onNodeWithText("Ads blocked by app").assertExists()
    }

    @Test fun tappingAnAppOpensItsNumbersAndTheInternetSwitch() {
        BlockStats.record("com.example.game", "ads.example.com", BlockCategory.ADS, System.currentTimeMillis())
        launchApp()
        openTab("tab_stats")
        scrollToTag("statsList", "chart_ADS")
        rule.onNodeWithContentDescription("com.example.game: 1").performClick()
        rule.onNodeWithText("Block this app's internet").assertExists()
    }
}
