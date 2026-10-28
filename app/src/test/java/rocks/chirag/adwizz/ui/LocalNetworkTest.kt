package rocks.chirag.adwizz.ui

import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Android 17's "Nearby devices" (local network) permission for the Devices setup page. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h900dp")
class LocalNetworkTest : UiTestBase() {
    private var clicks = 0

    @Test fun olderAndroidNeedsNoPermission() {
        assertTrue(localNetworkAllowed(ctx))
        assertEquals("android.permission.ACCESS_LOCAL_NETWORK", LOCAL_NETWORK_PERMISSION)
    }

    @Test fun asksFirst() {
        rule.setContent { AdWizzTheme { LocalNetworkNeeded(refused = false) { clicks++ } } }
        rule.onNodeWithText("Allow AdWizz to reach nearby devices").assertIsDisplayed()
        rule.onNodeWithText("Allow").performClick()
        assertEquals(1, clicks)
        rule.onNodeWithText("Nearby devices → Allow", substring = true).assertDoesNotExist()
    }

    @Test fun afterARefusalPointsToAppInfo() {
        rule.setContent { AdWizzTheme { LocalNetworkNeeded(refused = true) { clicks++ } } }
        rule.onNodeWithText("Permissions → Nearby devices → Allow", substring = true).assertExists()
        rule.onNodeWithText("Open App info").performClick()
        assertEquals(1, clicks)
    }
}
