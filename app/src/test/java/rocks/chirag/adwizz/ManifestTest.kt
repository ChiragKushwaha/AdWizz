package rocks.chirag.adwizz

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Manifest rules that keep AdWizz installable and its add-on channel private. */
class ManifestTest {
    private val xml = File("src/main/AndroidManifest.xml").readText()

    @Test fun noAccessibilityServiceInTheApp() {
        // Play Protect blocks downloaded apps that can use Accessibility: that lives in the add-on.
        assertFalse(xml.contains("BIND_ACCESSIBILITY_SERVICE"))
        assertFalse(xml.contains("android.accessibilityservice"))
    }

    @Test fun addonPermissionIsSignatureOnly() {
        val perm = Regex("""<permission\s+android:name="\$\{applicationId}\.permission\.FOCUS_STATE"\s+android:protectionLevel="(\w+)"""")
            .find(xml)
        assertEquals("signature", perm?.groupValues?.get(1))
    }

    @Test fun switchProviderIsGuardedByThatPermission() {
        val provider = Regex("""<provider[^>]*FocusStateProvider[^>]*/>""", RegexOption.DOT_MATCHES_ALL).find(xml)!!.value
        assertTrue(provider.contains("""android:permission="${'$'}{applicationId}.permission.FOCUS_STATE""""))
        assertTrue(provider.contains("""android:authorities="${'$'}{applicationId}.focusstate""""))
    }

    @Test fun canSeeTheAddonPackage() {
        assertTrue(xml.contains("""<package android:name="${'$'}{focusPackage}" />"""))
    }

    @Test fun asksForLocalNetworkAccessForTheSetupPage() {
        assertTrue(xml.contains("android.permission.ACCESS_LOCAL_NETWORK"))
    }
}
