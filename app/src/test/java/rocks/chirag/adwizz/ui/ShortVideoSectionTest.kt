package rocks.chirag.adwizz.ui

import android.content.pm.PackageInfo
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rocks.chirag.adwizz.data.AddonState
import rocks.chirag.adwizz.data.FocusAddon
import rocks.chirag.adwizz.data.Settings

/** "Block Reels & Shorts": install the add-on, then walk the user through Accessibility. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp")
class ShortVideoSectionTest : UiTestBase() {
    private val switch = "switch_Block Reels & Shorts"
    private val service = "${FocusAddon.PACKAGE}/rocks.chirag.adwizz.focus.ShortVideoBlockerService"

    @Before fun clean() {
        Settings(ctx).blockShortVideos = false
        FocusAddon.dismiss()
    }

    @After fun after() = FocusAddon.dismiss()

    private fun show() = rule.setContent {
        AdWizzTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ShortVideoSection() } }
    }

    @Suppress("DEPRECATION")
    private fun installAddon() =
        shadowOf(ctx.packageManager).installPackage(PackageInfo().apply { packageName = FocusAddon.PACKAGE; versionCode = 1 })

    private fun enableService() = AndroidSettings.Secure.putString(
        ctx.contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES, service
    )

    @Test fun turningOnWithoutTheAddonOffersToInstallIt() {
        show()
        rule.onNodeWithTag(switch).performClick()
        rule.onNodeWithText("Install the Reels Blocker add-on?").assertIsDisplayed()
        rule.onNodeWithText("Not now").performClick()
        rule.onNodeWithText("Install the Reels Blocker add-on?").assertDoesNotExist()
        assertFalse(Settings(ctx).blockShortVideos)
    }

    @Test fun switchOnWithoutTheAddonShowsInstallButton() {
        Settings(ctx).blockShortVideos = true
        show()
        rule.onNodeWithText("Needs the Reels Blocker add-on.").assertExists()
        rule.onNodeWithText("Install add-on").assertExists()
    }

    @Test fun showsDownloadProgress() {
        Settings(ctx).blockShortVideos = true
        FocusAddon.setStateForTest(AddonState.Downloading(0.4f))
        show()
        rule.onNodeWithText("Downloading the Reels Blocker add-on… 40%").assertExists()
        FocusAddon.setStateForTest(AddonState.Installing)
        rule.onNodeWithText("Installing the Reels Blocker add-on…").assertExists()
    }

    @Test fun explainsPlayProtectBlocks() {
        Settings(ctx).blockShortVideos = true
        FocusAddon.setStateForTest(AddonState.Failed("Blocked", blocked = true))
        show()
        rule.onNodeWithText("Google Play Protect blocked the add-on", substring = true).assertExists()
        rule.onNodeWithText("Try again").assertExists()
    }

    @Test fun showsOtherErrors() {
        Settings(ctx).blockShortVideos = true
        FocusAddon.setStateForTest(AddonState.Failed("HTTP 503", blocked = false))
        show()
        rule.onNodeWithText("Couldn't install the add-on: HTTP 503").assertExists()
    }

    @Test fun installedButOffShowsTheSetupGuide() {
        installAddon()
        Settings(ctx).blockShortVideos = true
        show()
        rule.onNodeWithTag("reelsGuide").assertExists()
        rule.onNodeWithText("Needs one more step", substring = true).assertExists()
    }

    @Test fun turningOnWithTheAddonButNoAccessibilityExplainsThenGuides() {
        installAddon()
        show()
        rule.onNodeWithTag(switch).performClick()
        rule.onNodeWithText("Allow the Reels Blocker to see which screen is open?").assertIsDisplayed()
        rule.onNodeWithText("Show me how").performClick()
        assertTrue(Settings(ctx).blockShortVideos)
        rule.onNodeWithTag("reelsGuide").assertExists()
    }

    @Test fun fullySetUpJustSwitchesOnAndOff() {
        installAddon(); enableService()
        show()
        rule.onNodeWithTag(switch).performClick()
        assertTrue(Settings(ctx).blockShortVideos)
        rule.onNodeWithTag("reelsGuide").assertDoesNotExist()
        rule.onNodeWithText("Install the Reels Blocker add-on?").assertDoesNotExist()
        rule.onNodeWithTag(switch).performClick()
        assertFalse(Settings(ctx).blockShortVideos)
    }
}
