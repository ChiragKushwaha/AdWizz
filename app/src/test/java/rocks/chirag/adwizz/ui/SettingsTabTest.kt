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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class SettingsTabTest : UiTestBase() {

    @Before fun open() {
        launchApp()
        openTab("tab_settings")
    }

    private fun switchState(title: String) = scrollToTag("settingsList", "switchToggle_$title")

    @Test fun protectionSwitchesStartOnAndPersistWhenTurnedOff() {
        listOf(
            "Block DNS bypass" to { s: Settings -> s.catchHardcodedDns },
            "Block trackers & analytics scripts" to { s: Settings -> s.blockTrackers },
            "Block malicious & crypto-mining scripts" to { s: Settings -> s.blockMalicious },
        ).forEach { (title, read) ->
            switchState(title).assertIsOn()
            scrollToTag("settingsList", "switch_$title").performClick()
            rule.waitForIdle()
            switchState(title).assertIsOff()
            assertFalse(title, read(Settings(ctx)))
            scrollToTag("settingsList", "switch_$title").performClick()
            rule.waitForIdle()
            switchState(title).assertIsOn()
            assertTrue(title, read(Settings(ctx)))
        }
    }

    @Test fun adListSwitchesReflectAndUpdateSettings() {
        Sources.ADS.forEach { src ->
            val title = src.name
            if (src.id in Sources.DEFAULT_ENABLED) switchState(title).assertIsOn() else switchState(title).assertIsOff()
            scrollToTag("settingsList", "switch_$title").performClick()
            rule.waitForIdle()
            assertEquals(src.id !in Sources.DEFAULT_ENABLED, src.id in Settings(ctx).enabledSources)
        }
    }

    @Test fun groupedListsAreNotShownIndividually() {
        // Tracker / security lists are controlled by one switch each, not listed one by one.
        rule.onNodeWithTag("settingsList").performScrollToNode(hasText("Allowlist (never blocked)"))
        rule.onNodeWithText("EasyPrivacy").assertDoesNotExist()
        rule.onNodeWithText("NoCoin").assertDoesNotExist()
    }

    @Test fun dnsPresetChipSavesServers() {
        // Click via the accessibility action: under Robolectric 4.17 a synthetic touch on a chip in
        // a horizontally-scrolling row inside a LazyColumn doesn't reach it (a real tap on the phone
        // does — checked on device). The action runs the same onClick.
        fun tapChip(name: String) {
            scrollTo("settingsList", name).performScrollTo()
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            rule.waitForIdle()
        }
        tapChip("Quad9")
        assertEquals("9.9.9.9", Settings(ctx).primaryDns)
        assertEquals("149.112.112.112", Settings(ctx).secondaryDns)
        tapChip("Google")
        assertEquals("8.8.8.8", Settings(ctx).primaryDns)
        rule.onNodeWithText("Google").assertIsSelected()
    }

    @Test fun customDnsSaveButtonAppearsAndValidates() {
        rule.onNodeWithText("Save DNS servers").assertDoesNotExist()
        val primary = rule.onNode(hasText("Primary") and hasSetTextAction())
        primary.performTextReplacement("not-an-ip")
        rule.onNodeWithText("Save DNS servers").performClick()
        rule.waitForIdle()
        assertEquals("1.1.1.1", Settings(ctx).primaryDns) // rejected

        primary.performTextReplacement("94.140.14.14")
        rule.onNodeWithText("Save DNS servers").performClick()
        rule.waitForIdle()
        assertEquals("94.140.14.14", Settings(ctx).primaryDns)
        rule.onNodeWithText("Save DNS servers").assertDoesNotExist()
    }
}
