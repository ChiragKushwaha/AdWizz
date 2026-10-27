package rocks.chirag.adwizz.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rocks.chirag.adwizz.data.Settings
import rocks.chirag.adwizz.data.Sources

/** Settings tab: allow/block lists, excluded apps, list updates and help. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class SettingsListsTest : UiTestBase() {

    @Before fun open() {
        launchApp()
        openTab("tab_settings")
    }

    private fun switchState(title: String) = scrollToTag("settingsList", "switchToggle_$title")

    @Test fun allowlistAddAndRemove() {
        val field = scrollToTag("settingsList", "adder_example.com")
        field.performTextInput("https://Shop.Example.org/cart")
        rule.onAllNodesWithContentDescription("Add")[0].performClick()
        rule.waitForIdle()
        assertEquals(setOf("shop.example.org"), Settings(ctx).allowlist)
        scrollTo("settingsList", "shop.example.org").assertIsDisplayed()

        rule.onAllNodesWithContentDescription("Remove")[0].performClick()
        rule.waitForIdle()
        assertTrue(Settings(ctx).allowlist.isEmpty())
        rule.onNodeWithText("shop.example.org").assertDoesNotExist()
    }

    @Test fun allowlistRejectsGarbage() {
        scrollToTag("settingsList", "adder_example.com").performTextInput("no dots here")
        rule.onAllNodesWithContentDescription("Add")[0].performClick()
        rule.waitForIdle()
        assertTrue(Settings(ctx).allowlist.isEmpty())
    }

    @Test fun customBlocklistAdd() {
        scrollTo("settingsList", "My blocked domains")
        scrollToTag("settingsList", "adder_ads.example.com").performTextInput("annoying.example")
        rule.onAllNodesWithContentDescription("Add").onLast().performClick()
        rule.waitForIdle()
        assertEquals(setOf("annoying.example"), Settings(ctx).customBlocklist)
    }

    @Test fun excludedCountShownOnButton() {
        Settings(ctx).excludedApps = setOf("com.a", "com.b")
        // Re-open the tab so it re-reads settings.
        openTab("tab_activity"); openTab("tab_settings")
        scrollTo("settingsList", "2 app(s) excluded", substring = true).assertIsDisplayed()
    }

    @Test fun updateListsButtonAndNeverUpdatedLabel() {
        scrollTo("settingsList", "Update lists").assertIsDisplayed()
        rule.onNodeWithText("Never updated").assertIsDisplayed()
    }

    @Test fun alwaysOnAndPrivateDnsHelpAreShown() {
        scrollTo("settingsList", "Always-on VPN").assertIsDisplayed()
        scrollTo("settingsList", "Private DNS must be Off or Automatic").assertIsDisplayed()
    }
}
