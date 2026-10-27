package rocks.chirag.adshield.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rocks.chirag.adshield.data.Status

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class WizardStageTest : UiTestBase() {
    private var status by mutableStateOf(Status.STOPPED)

    private fun show() = rule.setContent { AdShieldTheme { WizardStage(status) } }

    @Test fun adsShowWhileUnprotected() {
        show()
        rule.onNodeWithContentDescription("Screen full of ads").assertExists()
    }

    @Test fun spellClearsTheAdsWhenProtectionStarts() {
        show()
        rule.mainClock.autoAdvance = false
        status = Status.STARTING
        androidx.compose.runtime.snapshots.Snapshot.sendApplyNotifications()
        rule.mainClock.advanceTimeBy(SPELL_MS / 2L)
        // Mid-spell the stage already describes the cleared screen (state-driven), animation still running.
        rule.onNodeWithContentDescription("Wizard has cleared the ads from the screen").assertExists()
        rule.mainClock.advanceTimeBy(SPELL_MS.toLong())
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Wizard has cleared the ads from the screen").assertExists()
    }

    @Test fun adsComeBackWhenStopped() {
        status = Status.RUNNING
        show()
        status = Status.STOPPED
        rule.mainClock.advanceTimeBy(1000)
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Screen full of ads").assertExists()
    }

    @Test fun alreadyProtectedDoesNotReplayTheSpell() {
        status = Status.RUNNING
        show()
        rule.onNodeWithContentDescription("Wizard has cleared the ads from the screen").assertExists()
    }

    @Test fun errorCountsAsUnprotected() {
        status = Status.ERROR
        show()
        rule.onNodeWithContentDescription("Screen full of ads").assertExists()
    }

    @Test fun spellTextAndTiming() {
        assertEquals("Wingardium Leviosa!", SPELL)
        assertTrue(SPELL_MS in 1500..5000)
    }

    @Test fun skyRendersWithAndWithoutGround() {
        rule.setContent { AdShieldTheme { androidx.compose.foundation.layout.Column {
            PixelSky(androidx.compose.ui.Modifier.weight(1f), groundY = 300f)
            PixelSky(androidx.compose.ui.Modifier.weight(1f))
        } } }
        rule.waitForIdle()
    }

    @Test fun pixelButtonClicks() {
        var clicks = 0
        rule.setContent { AdShieldTheme { PixelButton("Go", onClick = { clicks++ }) } }
        rule.onNodeWithText("GO").performClick()
        rule.onNodeWithText("GO").assertHasClickAction()
        assertEquals(1, clicks)
    }
}
