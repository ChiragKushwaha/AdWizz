package rocks.chirag.adwizz.ui

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import rocks.chirag.adwizz.data.Status
import rocks.chirag.adwizz.data.VpnState

/** Shared setup: clean global state, and the app's real root (theme reveal host + theme + screen). */
abstract class UiTestBase {
    @get:Rule val rule = createComposeRule()
    protected val ctx: Context = ApplicationProvider.getApplicationContext()
    protected var toggles = 0

    @Before fun resetGlobals() {
        // Endless scenery animations would keep the test clock from ever going idle.
        Motion.ambientEnabled = false
        // Pretend the daily update check just ran, so UI tests never hit the network.
        ctx.getSharedPreferences("adwizz", android.content.Context.MODE_PRIVATE).edit()
            .putLong("update_checked", System.currentTimeMillis()).commit()
        VpnState.setStatus(Status.STOPPED)
        VpnState.resetSession()
        ThemeState.mode.value = ThemeMode.SYSTEM
        toggles = 0
    }

    protected fun launchApp() = rule.setContent {
        ThemeRevealHost(onApply = { applyTheme(ctx, it) }) {
            AdWizzTheme { AppScreen(onToggle = { toggles++ }) }
        }
    }

    protected fun openTab(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    /** Scrolls a lazy list until a node with [text] is composed, then returns it. */
    protected fun scrollTo(listTag: String, text: String, substring: Boolean = false): SemanticsNodeInteraction {
        rule.onNodeWithTag(listTag).performScrollToNode(hasText(text, substring = substring))
        return rule.onNodeWithText(text, substring = substring)
    }

    protected fun scrollToTag(listTag: String, tag: String): SemanticsNodeInteraction {
        rule.onNodeWithTag(listTag).performScrollToNode(hasTestTag(tag))
        return rule.onNodeWithTag(tag)
    }
}
