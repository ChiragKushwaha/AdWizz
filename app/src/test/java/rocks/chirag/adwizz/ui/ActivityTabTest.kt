package rocks.chirag.adwizz.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rocks.chirag.adwizz.data.Settings
import rocks.chirag.adwizz.data.VpnState

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class ActivityTabTest : UiTestBase() {

    @Test fun emptyStateMessage() {
        launchApp()
        rule.onNodeWithText("DNS requests from your apps will show up here once protection is on.").assertIsDisplayed()
    }

    @Test fun listsQueriesNewestFirstWithStatus() {
        VpnState.record("older-allowed.com", false)
        VpnState.record("newest-blocked.com", true)
        launchApp()
        rule.onNodeWithText("newest-blocked.com").assertIsDisplayed()
        rule.onNodeWithText("older-allowed.com").assertIsDisplayed()
        rule.onNodeWithText("BLOCKED").assertIsDisplayed()
        rule.onNodeWithText("allowed").assertIsDisplayed()
        val tops = listOf("newest-blocked.com", "older-allowed.com")
            .map { rule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(tops[0] < tops[1])
    }

    @Test fun allowingABlockedDomain() {
        VpnState.record("needed-cdn.com", true)
        Settings(ctx).customBlocklist = setOf("needed-cdn.com")
        launchApp()
        rule.onNodeWithText("needed-cdn.com").performClick()
        rule.onNodeWithText("This domain is blocked", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Allow").performClick()
        rule.waitForIdle()
        val s = Settings(ctx)
        assertTrue("needed-cdn.com" in s.allowlist)
        assertFalse("needed-cdn.com" in s.customBlocklist)
        rule.onNodeWithText("This domain is blocked", substring = true).assertDoesNotExist()
    }

    @Test fun blockingAnAllowedDomain() {
        VpnState.record("sneaky-tracker.io", false)
        Settings(ctx).allowlist = setOf("sneaky-tracker.io")
        launchApp()
        rule.onNodeWithText("sneaky-tracker.io").performClick()
        rule.onNodeWithText("Block").performClick()
        rule.waitForIdle()
        val s = Settings(ctx)
        assertTrue("sneaky-tracker.io" in s.customBlocklist)
        assertFalse("sneaky-tracker.io" in s.allowlist)
    }

    @Test fun cancelLeavesSettingsAlone() {
        VpnState.record("x.com", true)
        launchApp()
        rule.onNodeWithText("x.com").performClick()
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()
        assertTrue(Settings(ctx).allowlist.isEmpty())
        rule.onNodeWithText("Allow").assertDoesNotExist()
    }

    @Test fun newQueriesAppearLive() {
        launchApp()
        VpnState.record("live.example", true)
        rule.waitForIdle()
        rule.onNodeWithText("live.example").assertIsDisplayed()
    }
}
