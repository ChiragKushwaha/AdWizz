package rocks.chirag.adwizz.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.ref.WeakReference
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import rocks.chirag.adwizz.data.Status
import rocks.chirag.adwizz.data.VpnState

/** The real screen, rotated (the Activity is destroyed and created again, like on a phone). */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class RotationTest {
    @get:Rule(order = 0) val calm = object : ExternalResource() {
        override fun before() {
            Motion.ambientEnabled = false   // endless scenery animations would never let the UI go idle
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .getSharedPreferences("adwizz", android.content.Context.MODE_PRIVATE).edit()
                .putLong("update_checked", System.currentTimeMillis()).commit()
            VpnState.setStatus(Status.STOPPED)
            SetupServer.instance.stop()
        }
    }
    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private fun rotate() {
        rule.activityRule.scenario.recreate()
        rule.waitForIdle()
    }

    @Test fun selectedTabSurvives() {
        rule.onNodeWithTag("tab_settings").performClick()
        rotate()
        rule.onNodeWithTag("settingsList").assertExists()
    }

    @Test fun typedTextSurvives() {
        rule.onNodeWithTag("tab_settings").performClick()
        rule.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("adder_example.com"))
        rule.onNodeWithTag("adder_example.com").performTextInput("half.typed")
        rotate()
        rule.onNodeWithTag("settingsList").performScrollToNode(hasTestTag("adder_example.com"))
        rule.onNodeWithTag("adder_example.com").assertTextContains("half.typed")
    }

    @Test fun rotatingDoesNotLeakTheOldScreen() {
        lateinit var old: WeakReference<MainActivity>
        rule.activityRule.scenario.onActivity { old = WeakReference(it) }
        repeat(3) { rotate() }
        repeat(20) { if (old.get() == null) return@repeat; System.gc(); System.runFinalization(); Thread.sleep(50) }
        assertNull("The Activity from before the rotation is still in memory", old.get())
    }

    @Test fun setupLinkSurvivesRotation() {
        rule.onNodeWithTag("tab_devices").performClick()
        rule.waitUntil(5000) { SetupServer.instance.isRunning || rule.onAllNodesWithTag("noNetwork").fetchSemanticsNodes().isNotEmpty() }
        assumeTrue("no local network on this machine", SetupServer.instance.isRunning)
        val token = SetupServer.instance.token
        rotate()
        assertTrue(SetupServer.instance.isRunning)
        assertEquals("the QR code's link changed on rotation", token, SetupServer.instance.token)
        rule.onNodeWithTag("tab_home").performClick()
        rule.waitForIdle()
        assertFalse("leaving the tab should stop the server", SetupServer.instance.isRunning)
    }
}
