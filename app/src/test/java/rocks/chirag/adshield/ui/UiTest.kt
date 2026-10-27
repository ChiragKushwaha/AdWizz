package rocks.chirag.adshield.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import rocks.chirag.adshield.data.Settings
import rocks.chirag.adshield.data.Sources
import rocks.chirag.adshield.data.Status
import rocks.chirag.adshield.data.VpnState
import rocks.chirag.adshield.share.ShareServer

/** Shared setup: clean global state, and the app's real root (theme reveal host + theme + screen). */
abstract class UiTestBase {
    @get:Rule val rule = createComposeRule()
    protected val ctx: Context = ApplicationProvider.getApplicationContext()
    protected var toggles = 0

    @Before fun resetGlobals() {
        // Endless scenery animations would keep the test clock from ever going idle.
        Motion.ambientEnabled = false
        VpnState.setStatus(Status.STOPPED)
        VpnState.resetSession()
        ThemeState.mode.value = ThemeMode.SYSTEM
        toggles = 0
    }

    protected fun launchApp() = rule.setContent {
        ThemeRevealHost(onApply = { applyTheme(ctx, it) }) {
            AdShieldTheme { AppScreen(onToggle = { toggles++ }) }
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

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class HomeScreenTest : UiTestBase() {

    @Test fun showsNotProtectedWhenStopped() {
        launchApp()
        rule.onNodeWithText("Not protected").assertIsDisplayed()
        rule.onNodeWithText("Tap to block ads across your phone").assertIsDisplayed()
        rule.onNodeWithContentDescription("Turn protection on").assertExists()
    }

    @Test fun showsProtectedWhenRunning() {
        VpnState.setStatus(Status.RUNNING)
        launchApp()
        rule.onNodeWithText("Protected").assertIsDisplayed()
        rule.onNodeWithText("Ads and trackers are blocked in every app").assertIsDisplayed()
        rule.onNodeWithContentDescription("Turn protection off").assertExists()
    }

    @Test fun statusTextFollowsStateChanges() {
        launchApp()
        VpnState.setStatus(Status.RUNNING)
        rule.waitForIdle()
        rule.onNodeWithText("Protected").assertIsDisplayed()
        VpnState.setStatus(Status.STOPPED)
        rule.waitForIdle()
        rule.onNodeWithText("Not protected").assertIsDisplayed()
    }

    @Test fun showsErrorMessage() {
        VpnState.setStatus(Status.ERROR, "VPN permission not granted")
        launchApp()
        rule.onNodeWithText("Couldn't start").assertIsDisplayed()
        rule.onNodeWithText("VPN permission not granted").assertIsDisplayed()
    }

    @Test fun startingStateShowsAndPulses() {
        // The pulse is an infinite animation, so drive the clock by hand.
        rule.mainClock.autoAdvance = false
        VpnState.setStatus(Status.STARTING)
        launchApp()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("Starting…").assertExists()
    }

    @Test fun powerButtonCallsToggle() {
        launchApp()
        rule.onNodeWithTag("power").performClick()
        assertEquals(1, toggles)
        rule.onNodeWithTag("power").performClick()
        assertEquals(2, toggles)
    }

    @Test fun statsShowLiveCounts() {
        launchApp()
        VpnState.record("ads.com", true)
        VpnState.record("ok.com", false)
        VpnState.record("ads2.com", true)
        VpnState.record("ok2.com", false)
        rule.waitForIdle()
        rule.onNodeWithTag("stat_Blocked").assertTextEquals("2")
        rule.onNodeWithTag("stat_Queries").assertTextEquals("4")
        rule.onNodeWithTag("stat_Ratio").assertTextEquals("50%")
    }

    @Test fun listSizeIsCompacted() {
        VpnState.setListSize(791_234)
        launchApp()
        rule.onNodeWithTag("stat_List").assertTextEquals("791k")
        VpnState.setListSize(1_500_000)
        rule.waitForIdle()
        rule.onNodeWithTag("stat_List").assertTextEquals("1.5M")
        VpnState.setListSize(42)
        rule.waitForIdle()
        rule.onNodeWithTag("stat_List").assertTextEquals("42")
    }

    @Test fun allThreeTabsExistAndSwitch() {
        launchApp()
        rule.onNodeWithTag("tab_activity").assertIsSelected()
        openTab("tab_settings")
        rule.onNodeWithTag("tab_settings").assertIsSelected()
        rule.onNodeWithText("Appearance").assertIsDisplayed()
        openTab("tab_devices")
        rule.onNodeWithTag("tab_devices").assertIsSelected()
        rule.onNodeWithText("Protect other devices").assertIsDisplayed()
        openTab("tab_activity")
        rule.onNodeWithText("once protection is on", substring = true).assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class ActivityTabTest : UiTestBase() {

    @Test fun emptyStateMessage() {
        launchApp()
        rule.onNodeWithText("DNS requests from your apps will show up here once protection is on.").assertIsDisplayed()
    }

    @Test fun listsQueriesNewestFirstWithStatus() {
        VpnState.record("older-allowed.com", false)
        VpnState.record("newest-blocked.com", true)
        launchApp()
        rule.onNodeWithText("newest-blocked.com").assertIsDisplayed()
        rule.onNodeWithText("older-allowed.com").assertIsDisplayed()
        rule.onNodeWithText("BLOCKED").assertIsDisplayed()
        rule.onNodeWithText("allowed").assertIsDisplayed()
        val tops = listOf("newest-blocked.com", "older-allowed.com")
            .map { rule.onNodeWithText(it).fetchSemanticsNode().boundsInRoot.top }
        assertTrue(tops[0] < tops[1])
    }

    @Test fun allowingABlockedDomain() {
        VpnState.record("needed-cdn.com", true)
        Settings(ctx).customBlocklist = setOf("needed-cdn.com")
        launchApp()
        rule.onNodeWithText("needed-cdn.com").performClick()
        rule.onNodeWithText("This domain is blocked", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Allow").performClick()
        rule.waitForIdle()
        val s = Settings(ctx)
        assertTrue("needed-cdn.com" in s.allowlist)
        assertFalse("needed-cdn.com" in s.customBlocklist)
        rule.onNodeWithText("This domain is blocked", substring = true).assertDoesNotExist()
    }

    @Test fun blockingAnAllowedDomain() {
        VpnState.record("sneaky-tracker.io", false)
        Settings(ctx).allowlist = setOf("sneaky-tracker.io")
        launchApp()
        rule.onNodeWithText("sneaky-tracker.io").performClick()
        rule.onNodeWithText("Block").performClick()
        rule.waitForIdle()
        val s = Settings(ctx)
        assertTrue("sneaky-tracker.io" in s.customBlocklist)
        assertFalse("sneaky-tracker.io" in s.allowlist)
    }

    @Test fun cancelLeavesSettingsAlone() {
        VpnState.record("x.com", true)
        launchApp()
        rule.onNodeWithText("x.com").performClick()
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()
        assertTrue(Settings(ctx).allowlist.isEmpty())
        rule.onNodeWithText("Allow").assertDoesNotExist()
    }

    @Test fun newQueriesAppearLive() {
        launchApp()
        VpnState.record("live.example", true)
        rule.waitForIdle()
        rule.onNodeWithText("live.example").assertIsDisplayed()
    }
}

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
        AdShieldTheme { AppPickerContent(list, chosen, onChange = { chosen = it }) }
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
        rule.setContent { AdShieldTheme { AppScreen(onToggle = {}) } }
        rule.onNodeWithTag("themeToggle").performClick()
        rule.waitForIdle()
        assertEquals(ThemeMode.DARK, ThemeState.mode.value)
    }
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class DevicesTabTest : UiTestBase() {
    private val server = ShareServer(preferredPorts = 18200..18210)

    @After fun stop() = server.stop()

    private fun show(ip: String?) = rule.setContent {
        AdShieldTheme { DevicesTab(server = server, findIp = { ip }) }
    }

    @Test fun showsQrAndUrlWhenReachable() {
        show("127.0.0.1")
        rule.waitUntil(5000) { rule.onAllNodesWithTag("shareUrl").fetchSemanticsNodes().isNotEmpty() }
        val expected = "http://127.0.0.1:${server.port}/${server.token}/"
        rule.onNodeWithTag("shareUrl").assertTextEquals(expected)
        rule.onNodeWithTag("qr").assertExists()
        rule.onNodeWithContentDescription("QR code for $expected").assertExists()
        rule.onNodeWithText("Copy link").assertIsDisplayed()
        assertTrue(server.isRunning)
    }

    @Test fun copyLinkPutsUrlOnClipboard() {
        show("127.0.0.1")
        rule.waitUntil(5000) { rule.onAllNodesWithTag("shareUrl").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Copy link").performClick()
        val cm = ctx.getSystemService(android.content.ClipboardManager::class.java)
        assertEquals("http://127.0.0.1:${server.port}/${server.token}/", cm.primaryClip!!.getItemAt(0).text.toString())
    }

    @Test fun showsHotspotHintWhenUnreachable() {
        show(null)
        rule.waitUntil(5000) { rule.onAllNodesWithTag("noNetwork").fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Turn on your hotspot or join a Wi-Fi network").assertIsDisplayed()
        rule.onNodeWithTag("qr").assertDoesNotExist()
    }

    @Test fun platformInstructionsAreShown() {
        show(null)
        rule.onNodeWithTag("devices").performScrollToNode(hasText("Windows / laptops"))
        rule.onNodeWithText("iPhone, iPad, Mac").assertExists()
        rule.onNodeWithText("Android").assertExists()
        rule.onNodeWithText("dns.adguard-dns.com").assertExists()
    }

    @Test fun serverStopsWhenScreenLeaves() {
        var visible by mutableStateOf(true)
        rule.setContent { AdShieldTheme { if (visible) DevicesTab(server = server, findIp = { "127.0.0.1" }) } }
        rule.waitUntil(5000) { server.isRunning }
        visible = false
        rule.waitForIdle()
        assertFalse(server.isRunning)
    }
}


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

class WizardArtTest {
    @Test fun framesAreFullSizeAndUseOnlyPaletteColours() {
        listOf(WizardArt.IDLE, WizardArt.CASTING).forEach { frame ->
            assertEquals(WizardArt.HEIGHT, frame.size)
            frame.forEach { row ->
                assertEquals(WizardArt.WIDTH, row.length)
                row.forEach { ch -> assertTrue("unknown colour '$ch'", ch == '.' || ch in WizardArt.palette) }
            }
        }
    }

    @Test fun wandTipIsDrawnInTheCastingFrame() {
        val tip = WizardArt.CASTING[WizardArt.WAND_Y][WizardArt.WAND_X]
        assertEquals('S', tip)
        assertNotEquals(WizardArt.IDLE.joinToString(), WizardArt.CASTING.joinToString())
    }

    @Test fun wizardHasHatHairCloakEyesAndBroom() {
        val all = WizardArt.CASTING.joinToString("")
        listOf('H', 'N', 'C', 'K', 'Y', 'F', 'E').forEach { assertTrue("missing '$it'", it in all) }
    }
}

class DesignArtTest {
    @Test fun spritesAreRectangularAndUseTheirPalettes() {
        listOf(DesignArt.BAT to DesignArt.BAT_PALETTE, DesignArt.HEART to DesignArt.HEART_PALETTE, DesignArt.OWL to DesignArt.OWL_PALETTE).forEach { (rows, pal) ->
            assertTrue(rows.isNotEmpty())
            rows.forEach { r ->
                assertEquals(rows[0].length, r.length)
                r.forEach { ch -> assertTrue("'$ch'", ch == '.' || ch in pal) }
            }
        }
    }

    /** Matches the bat in design/image.png: wide wings, two ears, yellow eyes, little tail. */
    @Test fun batLooksLikeTheDesign() {
        assertEquals(listOf("OOO.O.O.OOO", ".OOOYOYOOO.", "..O.OOO.O..", ".....O....."), DesignArt.BAT.toList())
    }

    /** Heart is symmetric apart from its shine, outlined, with a single point at the bottom. */
    @Test fun heartShape() {
        val h = DesignArt.HEART
        assertEquals(".....O.....", h.last())
        h.forEach { r -> assertEquals(r.replace('W', 'R'), r.replace('W', 'R').reversed()) }
        assertTrue(h.joinToString("").contains('W'))
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

class OwlArtTest {
    @Test fun owlHasTwoYellowEyesAndOutline() {
        val all = DesignArt.OWL.joinToString("")
        assertEquals(2, all.count { it == 'Y' })
        assertTrue(all.count { it == 'O' } > 30)
        assertTrue(all.count { it == 'W' } > 40)  // a white snowy owl
    }
}
