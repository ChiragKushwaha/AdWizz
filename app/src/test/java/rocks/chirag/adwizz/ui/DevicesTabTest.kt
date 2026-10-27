package rocks.chirag.adwizz.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rocks.chirag.adwizz.share.ShareServer

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class DevicesTabTest : UiTestBase() {
    private val server = ShareServer(preferredPorts = 18200..18210)

    @After fun stop() = server.stop()

    private fun show(ip: String?) = rule.setContent {
        AdWizzTheme { DevicesTab(server = server, findIp = { ip }) }
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
        rule.setContent { AdWizzTheme { if (visible) DevicesTab(server = server, findIp = { "127.0.0.1" }) } }
        rule.waitUntil(5000) { server.isRunning }
        visible = false
        rule.waitForIdle()
        assertFalse(server.isRunning)
    }
}
