package rocks.chirag.adwizz.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rocks.chirag.adwizz.data.FocusAddon
import rocks.chirag.adwizz.data.QueryLog
import rocks.chirag.adwizz.data.Settings
import rocks.chirag.adwizz.data.VpnState

/** What the user is in the middle of (dialogs, typed text, ticked apps) survives a rotation. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h2000dp")
class StateRestorationTest : UiTestBase() {
    private val restore by lazy { StateRestorationTester(rule) }

    @Test fun queryLogSaverRoundTrips() {
        val q = QueryLog("ads.example.com", true, 1234L)
        val scope = androidx.compose.runtime.saveable.SaverScope { true }
        val saved: Any = with(QueryLogSaver) { with(scope) { save(q) } }!!
        assertEquals(q, QueryLogSaver.restore(saved))
        assertNull(QueryLogSaver.restore(emptyList<Any>()))
    }

    @Test fun activityDialogSurvives() {
        VpnState.record("ads.example.com", blocked = true)
        restore.setContent { AdWizzTheme { ActivityTab() } }
        rule.onNodeWithText("ads.example.com").performClick()
        rule.onNodeWithText("Allow").assertIsDisplayed()
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Allow").assertIsDisplayed()
        rule.onNodeWithText("This domain is blocked", substring = true).assertIsDisplayed()
    }

    @Test fun halfTypedDomainSurvives() {
        restore.setContent { AdWizzTheme { DomainAdder("example.com") { } } }
        rule.onNodeWithTag("adder_example.com").performTextInput("half.typed")
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("adder_example.com").assertTextContains("half.typed")
    }

    @Test fun appSearchAndTicksSurvive() {
        val apps = listOf(AppEntry("com.bank.app", "Bank"), AppEntry("com.game.app", "Game"))
        var latest = emptySet<String>()
        restore.setContent {
            var chosen by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = StringSetSaver) { mutableStateOf(emptySet<String>()) }
            latest = chosen
            AdWizzTheme { AppPickerContent(apps, chosen) { chosen = it } }
        }
        rule.onNodeWithTag("appSearch").performTextInput("ban")
        rule.onNodeWithText("Bank").performClick()
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithTag("appSearch").assertTextContains("ban")
        rule.onNodeWithText("Game").assertDoesNotExist()   // still filtered
        rule.waitForIdle()
        assertEquals(setOf("com.bank.app"), latest)   // the tick came back too
    }

    @Test fun reelsDialogSurvives() {
        Settings(ctx).blockShortVideos = false
        FocusAddon.dismiss()
        restore.setContent { AdWizzTheme { Column(Modifier.verticalScroll(rememberScrollState())) { ShortVideoSection() } } }
        rule.onNodeWithTag("switch_Block Reels & Shorts").performClick()
        rule.onNodeWithText("Install the Reels Blocker add-on?").assertIsDisplayed()
        restore.emulateSavedInstanceStateRestore()
        rule.onNodeWithText("Install the Reels Blocker add-on?").assertIsDisplayed()
    }
}
