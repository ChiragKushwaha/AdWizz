package rocks.chirag.adwizz.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The illustrated steps for switching the Reels Blocker add-on on. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h1600dp")
class ReelsSetupGuideTest : UiTestBase() {
    private var accessibility = 0
    private var appInfo = 0

    private fun show(restricted: Boolean) = rule.setContent {
        AdWizzTheme { ReelsSetupGuide({ accessibility++ }, { appInfo++ }, restricted) }
    }

    @Test fun restrictedSettingsOnlyApplyFromAndroid13() {
        assertFalse(needsRestrictedSettingsSteps(32))
        assertTrue(needsRestrictedSettingsSteps(33))
        assertTrue(needsRestrictedSettingsSteps(37))
    }

    @Test fun android13AndLaterShowsThreeIllustratedSteps() {
        show(restricted = true)
        listOf("guideStep1", "guideStep2", "guideStep3").forEach { rule.onNodeWithTag(it).assertExists() }
        rule.onNodeWithText("Restricted setting", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Allow restricted settings", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("⋮", useUnmergedTree = true).assertExists()
        rule.onNodeWithContentDescription("Accessibility settings showing the Restricted setting message").assertExists()
        rule.onNodeWithContentDescription("App info for AdWizz Reels Blocker", substring = true).assertExists()
        rule.onNodeWithContentDescription("Accessibility, Installed apps: turn on AdWizz Reels Blocker").assertExists()
    }

    @Test fun buttonsOpenTheRightSettings() {
        show(restricted = true)
        rule.onNodeWithTag("guideButton1").performClick()
        rule.onNodeWithTag("guideButton2").performClick()
        rule.onNodeWithTag("guideButton3").performClick()
        assertEquals(2, accessibility)
        assertEquals(1, appInfo)
        rule.onNodeWithText("Open add-on App info").assertExists()
    }

    @Test fun olderAndroidJustNeedsTheSwitch() {
        show(restricted = false)
        rule.onNodeWithTag("guideStep1").assertExists()
        rule.onNodeWithTag("guideStep2").assertDoesNotExist()
        rule.onNodeWithTag("guideButton1").performClick()
        assertEquals(1, accessibility)
        assertEquals(0, appInfo)
    }
}
