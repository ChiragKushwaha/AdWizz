package rocks.chirag.adwizz.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rocks.chirag.adwizz.data.Status
import rocks.chirag.adwizz.data.VpnState

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class HomeScreenTest : UiTestBase() {

    @Test fun showsNotProtectedWhenStopped() {
        launchApp()
        rule.onNodeWithText("Not protected").assertIsDisplayed()
        rule.onNodeWithText("Tap to block ads across your phone").assertIsDisplayed()
        rule.onNodeWithContentDescription("Turn protection on").assertExists()
    }

    @Test fun showsProtectedWhenRunning() {
        VpnState.setStatus(Status.RUNNING)
        launchApp()
        rule.onNodeWithText("Protected").assertIsDisplayed()
        rule.onNodeWithText("Ads and trackers are blocked in every app").assertIsDisplayed()
        rule.onNodeWithContentDescription("Turn protection off").assertExists()
    }

    @Test fun statusTextFollowsStateChanges() {
        launchApp()
        VpnState.setStatus(Status.RUNNING)
        rule.waitForIdle()
        rule.onNodeWithText("Protected").assertIsDisplayed()
        VpnState.setStatus(Status.STOPPED)
        rule.waitForIdle()
        rule.onNodeWithText("Not protected").assertIsDisplayed()
    }

    @Test fun showsErrorMessage() {
        VpnState.setStatus(Status.ERROR, "VPN permission not granted")
        launchApp()
        rule.onNodeWithText("Couldn't start").assertIsDisplayed()
        rule.onNodeWithText("VPN permission not granted").assertIsDisplayed()
    }

    @Test fun startingStateShowsAndPulses() {
        // The pulse is an infinite animation, so drive the clock by hand.
        rule.mainClock.autoAdvance = false
        VpnState.setStatus(Status.STARTING)
        launchApp()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("Starting…").assertExists()
    }

    @Test fun powerButtonCallsToggle() {
        launchApp()
        rule.onNodeWithTag("power").performClick()
        assertEquals(1, toggles)
        rule.onNodeWithTag("power").performClick()
        assertEquals(2, toggles)
    }

    @Test fun statsShowLiveCounts() {
        launchApp()
        VpnState.record("ads.com", true)
        VpnState.record("ok.com", false)
        VpnState.record("ads2.com", true)
        VpnState.record("ok2.com", false)
        rule.waitForIdle()
        rule.onNodeWithTag("stat_Blocked").assertTextEquals("2")
        rule.onNodeWithTag("stat_Queries").assertTextEquals("4")
        rule.onNodeWithTag("stat_Ratio").assertTextEquals("50%")
    }

    @Test fun listSizeIsCompacted() {
        VpnState.setListSize(791_234)
        launchApp()
        rule.onNodeWithTag("stat_List").assertTextEquals("791k")
        VpnState.setListSize(1_500_000)
        rule.waitForIdle()
        rule.onNodeWithTag("stat_List").assertTextEquals("1.5M")
        VpnState.setListSize(42)
        rule.waitForIdle()
        rule.onNodeWithTag("stat_List").assertTextEquals("42")
    }

    @Test fun allThreeTabsExistAndSwitch() {
        launchApp()
        rule.onNodeWithTag("tab_activity").assertIsSelected()
        openTab("tab_settings")
        rule.onNodeWithTag("tab_settings").assertIsSelected()
        rule.onNodeWithText("Appearance").assertIsDisplayed()
        openTab("tab_devices")
        rule.onNodeWithTag("tab_devices").assertIsSelected()
        rule.onNodeWithText("Protect other devices").assertIsDisplayed()
        openTab("tab_activity")
        rule.onNodeWithText("once protection is on", substring = true).assertIsDisplayed()
    }
}
