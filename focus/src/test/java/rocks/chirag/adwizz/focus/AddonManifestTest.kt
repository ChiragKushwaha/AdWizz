package rocks.chirag.adwizz.focus

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** The add-on holds the Accessibility service, and reads AdWizz's switch through its signature permission. */
class AddonManifestTest {
    private val xml = File("src/main/AndroidManifest.xml").readText()

    @Test fun declaresTheAccessibilityService() {
        assertTrue(xml.contains("android:name=\".ShortVideoBlockerService\""))
        assertTrue(xml.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"))
        assertTrue(xml.contains("@xml/short_video_blocker"))
    }

    @Test fun serviceIsNotExportedToOtherApps() {
        assertTrue(Regex("""ShortVideoBlockerService"\s+android:exported="false"""").containsMatchIn(xml))
    }

    @Test fun usesAdWizzsSignaturePermissionAndCanSeeIt() {
        assertTrue(xml.contains("""<uses-permission android:name="${'$'}{mainAppId}.permission.FOCUS_STATE" />"""))
        assertTrue(xml.contains("""<package android:name="${'$'}{mainAppId}" />"""))
    }

    @Test fun keepsNoDataOfItsOwn() {
        assertTrue(xml.contains("android:allowBackup=\"false\""))
        assertFalse(xml.contains("android.permission.INTERNET"))
    }
}
