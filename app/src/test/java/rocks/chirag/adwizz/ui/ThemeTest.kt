package rocks.chirag.adwizz.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rocks.chirag.adwizz.data.Settings

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class ThemeTest : UiTestBase() {

    @Test fun themeModeParsing() {
        assertEquals(ThemeMode.DARK, ThemeMode.from("dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.from("light"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from("system"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.from("garbage"))
        assertEquals(listOf("System", "Light", "Dark"), ThemeMode.entries.map { it.label })
    }

    @Test fun topBarToggleFlipsAndPersists() {
        ThemeState.mode.value = ThemeMode.LIGHT
        launchApp()
        rule.onNodeWithContentDescription("Switch to dark mode").assertExists()
        rule.onNodeWithTag("themeToggle").performClick()
        rule.waitForIdle()
        assertEquals(ThemeMode.DARK, ThemeState.mode.value)
        assertEquals("dark", Settings(ctx).themeMode)
        rule.onNodeWithContentDescription("Switch to light mode").assertExists()

        rule.onNodeWithTag("themeToggle").performClick()
        rule.waitForIdle()
        assertEquals(ThemeMode.LIGHT, ThemeState.mode.value)
        assertEquals("light", Settings(ctx).themeMode)
    }

    @Test fun segmentedButtonsSelectEachMode() {
        launchApp()
        openTab("tab_settings")
        rule.onNodeWithTag("theme_system").assertIsSelected()
        listOf(ThemeMode.DARK, ThemeMode.LIGHT, ThemeMode.SYSTEM).forEach { mode ->
            rule.onNodeWithTag("theme_${mode.key}").performClick()
            rule.waitForIdle()
            assertEquals(mode, ThemeState.mode.value)
            assertEquals(mode.key, Settings(ctx).themeMode)
            rule.onNodeWithTag("theme_${mode.key}").assertIsSelected()
        }
    }

    @Test fun rapidTogglingEndsOnTheLastChoice() {
        ThemeState.mode.value = ThemeMode.LIGHT
        launchApp()
        repeat(5) { rule.onNodeWithTag("themeToggle").performClick() }
        rule.waitForIdle()
        // Odd number of flips from light.
        assertEquals(ThemeMode.DARK, ThemeState.mode.value)
    }

    @Test fun revealControllerAppliesModeWithoutUi() {
        var applied: ThemeMode? = null
        lateinit var controller: ThemeRevealController
        rule.setContent {
            ThemeRevealHost(onApply = { applied = it }) {
                controller = LocalThemeReveal.current!!
            }
        }
        rule.runOnIdle { controller.switchTo(ThemeMode.DARK, Offset(10f, 10f)) }
        rule.mainClock.advanceTimeBy(THEME_REVEAL_MS + 500L)
        rule.waitForIdle()
        assertEquals(ThemeMode.DARK, applied)
    }

    @Test fun revealCompletesAndRemovesOverlay() {
        ThemeState.mode.value = ThemeMode.LIGHT
        launchApp()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("themeToggle").performClick()
        rule.mainClock.advanceTimeBy(THEME_REVEAL_MS / 2L)
        // Mid-animation: new theme already applied underneath the old-theme snapshot.
        assertEquals(ThemeMode.DARK, ThemeState.mode.value)
        rule.mainClock.advanceTimeBy(THEME_REVEAL_MS.toLong() + 500)
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        // The UI is fully usable afterwards.
        rule.onNodeWithTag("power").performClick()
        assertEquals(1, toggles)
    }

    @Test fun withoutHostThemeStillSwitches() {
        ThemeState.mode.value = ThemeMode.LIGHT
        rule.setContent { AdWizzTheme { AppScreen(onToggle = {}) } }
        rule.onNodeWithTag("themeToggle").performClick()
        rule.waitForIdle()
        assertEquals(ThemeMode.DARK, ThemeState.mode.value)
    }
}
