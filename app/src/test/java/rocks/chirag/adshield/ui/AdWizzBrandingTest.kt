package rocks.chirag.adshield.ui

import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rocks.chirag.adshield.data.Status
import rocks.chirag.adshield.data.VpnState

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class AdWizzBrandingTest : UiTestBase() {

    @Test fun appIsCalledAdWizz() {
        assertEquals("AdWizz", ctx.getString(rocks.chirag.adshield.R.string.app_name))
        launchApp()
        rule.onNodeWithText("AdWizz").assertIsDisplayed()
        rule.onAllNodesWithText("AdShield", substring = true).assertCountEquals(0)
    }

    @Test fun buttonLabelFollowsStatus() {
        launchApp()
        rule.onNodeWithText("CAST SPELL").assertIsDisplayed()
        VpnState.setStatus(Status.STARTING); rule.waitForIdle()
        rule.onNodeWithText("CASTING…").assertIsDisplayed()
        VpnState.setStatus(Status.RUNNING); rule.waitForIdle()
        rule.onNodeWithText("STOP").assertIsDisplayed()
        VpnState.setStatus(Status.ERROR, "x"); rule.waitForIdle()
        rule.onNodeWithText("CAST SPELL").assertIsDisplayed()
    }

    @Test fun pixelFontIsUsedByTheTheme() {
        var family: androidx.compose.ui.text.font.FontFamily? = null
        rule.setContent { AdShieldTheme { family = androidx.compose.material3.MaterialTheme.typography.bodyMedium.fontFamily } }
        assertEquals(PixelFont, family)
    }

    @Test fun sceneColoursSwitchWithTheme() {
        var scene: SceneColors? = null
        ThemeState.mode.value = ThemeMode.DARK
        rule.setContent { AdShieldTheme { scene = sceneColors() } }
        assertEquals(DarkScene, scene)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class SkyToggleTest : UiTestBase() {
    @Test fun noHeartsInTopBarAndToggleIsTheSunOrMoon() {
        ThemeState.mode.value = ThemeMode.LIGHT
        launchApp()
        // Sun in daytime -> tapping it switches to dark.
        rule.onNodeWithContentDescription("Switch to dark mode").assertIsDisplayed().assertHasClickAction()
        rule.onNodeWithTag("themeToggle").performClick()
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Switch to light mode").assertIsDisplayed()
    }

    @Test fun celestialSpritesRender() {
        rule.setContent { AdShieldTheme { androidx.compose.foundation.layout.Row {
            PixelCelestial(moon = false, modifier = androidx.compose.ui.Modifier.testTag("sun").then(androidx.compose.ui.Modifier.size(48.dp)))
            PixelCelestial(moon = true, modifier = androidx.compose.ui.Modifier.testTag("moon").then(androidx.compose.ui.Modifier.size(48.dp)))
        } } }
        rule.onNodeWithTag("sun").assertExists()
        rule.onNodeWithTag("moon").assertExists()
    }
}
