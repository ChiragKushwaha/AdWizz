package rocks.chirag.adwizz.ui

import androidx.compose.foundation.layout.size
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

/**
 * The picker's dialog window never reports idle under Robolectric (it works on a real phone), so
 * the picker body is tested directly, plus the pure filter function.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class AppPickerTest : UiTestBase() {
    private val apps = listOf(
        AppEntry("com.bank.mobile", "My Bank"),
        AppEntry("com.netflix.mediaclient", "Netflix"),
        AppEntry("com.whatsapp", "WhatsApp"),
    )
    private var chosen by mutableStateOf(emptySet<String>())

    private fun show(list: List<AppEntry>?) = rule.setContent {
        AdWizzTheme { AppPickerContent(list, chosen, onChange = { chosen = it }) }
    }

    @Test fun loadingState() {
        show(null)
        rule.onNodeWithText("Loading apps…").assertIsDisplayed()
    }

    @Test fun listsAllApps() {
        show(apps)
        apps.forEach { rule.onNodeWithText(it.label).assertIsDisplayed() }
        rule.onNodeWithText("com.whatsapp").assertIsDisplayed()
    }

    @Test fun tappingTogglesSelection() {
        show(apps)
        rule.onNodeWithTag("app_com.bank.mobile").performClick()
        rule.waitForIdle()
        assertEquals(setOf("com.bank.mobile"), chosen)
        rule.onNodeWithTag("app_com.netflix.mediaclient").performClick()
        rule.onNodeWithTag("app_com.bank.mobile").performClick()
        rule.waitForIdle()
        assertEquals(setOf("com.netflix.mediaclient"), chosen)
    }

    @Test fun searchFiltersByNameAndPackage() {
        show(apps)
        rule.onNodeWithTag("appSearch").performTextInput("bank")
        rule.waitForIdle()
        rule.onNodeWithText("My Bank").assertIsDisplayed()
        rule.onNodeWithText("Netflix").assertDoesNotExist()
        rule.onNodeWithTag("appSearch").performTextReplacement("mediaclient")
        rule.waitForIdle()
        rule.onNodeWithText("Netflix").assertIsDisplayed()
        rule.onNodeWithText("My Bank").assertDoesNotExist()
    }

    @Test fun noMatchMessage() {
        show(apps)
        rule.onNodeWithTag("appSearch").performTextInput("zzz")
        rule.waitForIdle()
        rule.onNodeWithText("No apps match \"zzz\"").assertIsDisplayed()
    }

    @Test fun noAppsMessage() {
        show(emptyList())
        rule.onNodeWithText("No apps found").assertIsDisplayed()
    }

    @Test fun filterPutsSelectedFirstAndIgnoresCaseAndSpaces() {
        val r = filterApps(apps, "", setOf("com.whatsapp"))
        assertEquals("com.whatsapp", r.first().pkg)
        assertEquals(listOf("com.netflix.mediaclient"), filterApps(apps, "  NETFLIX ", emptySet()).map { it.pkg })
        assertEquals(3, filterApps(apps, "   ", emptySet()).size)
        assertTrue(filterApps(apps, "nothing", emptySet()).isEmpty())
    }

    @Test fun loadAppsSkipsItself() {
        val list = loadApps(ctx)
        assertTrue(list.none { it.pkg == ctx.packageName })
        assertEquals(list.sortedBy { it.label.lowercase() }, list)
    }
}
