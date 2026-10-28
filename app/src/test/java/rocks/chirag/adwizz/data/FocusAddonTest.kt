package rocks.chirag.adwizz.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.provider.Settings as AndroidSettings
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The Reels Blocker add-on: manifest entry, install results, and "is it installed / switched on". */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class FocusAddonTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val sha = "b".repeat(64)
    private val url = "https://github.com/ChiragKushwaha/AdWizz/releases/download/reels-blocker-v1.0.0/AdWizzReelsBlocker.apk"
    private val service = "${FocusAddon.PACKAGE}/rocks.chirag.adwizz.focus.ShortVideoBlockerService"

    private fun manifest(focus: String?) = """{"versionCode":5,"versionName":"1.3.0",
        "apkUrl":"https://github.com/ChiragKushwaha/AdWizz/releases/download/v1.3.0/AdWizz.apk",
        "sha256":"${"a".repeat(64)}","sizeBytes":1900000,"notes":"x"${if (focus != null) ""","focus":$focus""" else ""}}"""

    private fun addon(versionCode: Any = 1, apkUrl: String = url, sha256: String = sha, size: Long = 52_000) =
        """{"versionCode":$versionCode,"apkUrl":"$apkUrl","sha256":"$sha256","sizeBytes":$size}"""

    @Before fun reset() = FocusAddon.dismiss()

    @Test fun parsesTheAddonEntry() {
        val info = UpdateManifest.parseAddon(manifest(addon()))!!
        assertEquals(1, info.versionCode)
        assertEquals(url, info.apkUrl)
        assertEquals(sha, info.sha256)
        assertEquals(52_000L, info.sizeBytes)
    }

    @Test fun theAppEntryStillParsesNextToTheAddon() {
        assertEquals(5, UpdateManifest.parse(manifest(addon()))!!.versionCode)
    }

    @Test fun noAddonEntryMeansNull() {
        assertNull(UpdateManifest.parseAddon(manifest(null)))
        assertNull(UpdateManifest.parseAddon("not json"))
    }

    @Test fun rejectsUnsafeAddonEntries() {
        assertNull(UpdateManifest.parseAddon(manifest(addon(apkUrl = "https://evil.example/AdWizzReelsBlocker.apk"))))
        assertNull(UpdateManifest.parseAddon(manifest(addon(apkUrl = url.replace("https", "http")))))
        assertNull(UpdateManifest.parseAddon(manifest(addon(sha256 = "xyz"))))
        assertNull(UpdateManifest.parseAddon(manifest(addon(size = UpdateManifest.MAX_APK_BYTES + 1))))
        assertNull(UpdateManifest.parseAddon(manifest(addon(versionCode = 0))))
        assertNull(UpdateManifest.parseAddon(manifest(addon(versionCode = "\"one\""))))
    }

    @Test fun playProtectBlocksAreRecognised() {
        FocusAddon.onInstallFailed(PackageInstaller.STATUS_FAILURE_BLOCKED, "Blocked")
        assertEquals(AddonState.Failed("Blocked", blocked = true), FocusAddon.state.value)
        FocusAddon.onInstallFailed(PackageInstaller.STATUS_FAILURE, "Blocked by Play Protect")
        assertTrue((FocusAddon.state.value as AddonState.Failed).blocked)
    }

    @Test fun otherFailuresAreRetryable() {
        FocusAddon.onInstallFailed(PackageInstaller.STATUS_FAILURE_STORAGE, "Not enough space")
        assertEquals(AddonState.Failed("Not enough space", blocked = false), FocusAddon.state.value)
    }

    @Test fun cancellingAndroidsInstallScreenIsNotAnError() {
        FocusAddon.onInstallFailed(PackageInstaller.STATUS_FAILURE_ABORTED, "User cancelled")
        assertEquals(AddonState.Idle, FocusAddon.state.value)
    }

    @Test fun finishedInstallsAreCounted() {
        val before = FocusAddon.installs.value
        FocusAddon.setStateForTest(AddonState.Installing)
        FocusAddon.onInstalled()
        assertEquals(AddonState.Idle, FocusAddon.state.value)
        assertEquals(before + 1, FocusAddon.installs.value)
    }

    @Test fun installResultsGoToTheAddonOrTheUpdater() {
        fun result(target: String, status: Int, msg: String = "nope") = Intent()
            .putExtra("target", target).putExtra(PackageInstaller.EXTRA_STATUS, status)
            .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, msg)

        InstallResultReceiver().onReceive(ctx, result(ApkInstaller.TARGET_ADDON, PackageInstaller.STATUS_FAILURE_BLOCKED))
        assertTrue((FocusAddon.state.value as AddonState.Failed).blocked)

        Updater.dismiss()
        InstallResultReceiver().onReceive(ctx, result(ApkInstaller.TARGET_SELF, PackageInstaller.STATUS_FAILURE, "update failed"))
        assertEquals("update failed", (Updater.state.value as UpdateState.Failed).message)

        val before = FocusAddon.installs.value
        InstallResultReceiver().onReceive(ctx, result(ApkInstaller.TARGET_ADDON, PackageInstaller.STATUS_SUCCESS))
        assertEquals(before + 1, FocusAddon.installs.value)
        Updater.dismiss()
    }

    @Test fun knowsWhetherTheAddonIsInstalled() {
        assertFalse(FocusAddon.isInstalled(ctx))
        @Suppress("DEPRECATION")
        shadowOf(ctx.packageManager).installPackage(PackageInfo().apply { packageName = FocusAddon.PACKAGE; versionCode = 3 })
        assertTrue(FocusAddon.isInstalled(ctx))
        assertEquals(3L, FocusAddon.installedVersion(ctx))
    }

    @Test fun knowsWhetherItsAccessibilityServiceIsOn() {
        val cr = ctx.contentResolver
        assertFalse(FocusAddon.isServiceEnabled(ctx))
        AndroidSettings.Secure.putString(cr, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "com.other/.Svc")
        assertFalse(FocusAddon.isServiceEnabled(ctx))
        AndroidSettings.Secure.putString(cr, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES, "com.other/.Svc:$service")
        assertTrue(FocusAddon.isServiceEnabled(ctx))
    }

    @Test fun appInfoOpensTheAddonsPage() {
        val i = FocusAddon.appInfoIntent()
        assertEquals(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, i.action)
        assertEquals("package:${FocusAddon.PACKAGE}", i.dataString)
    }

    @Test fun debugBuildsPairWithTheDebugAddon() {
        assertEquals("rocks.chirag.adwizz.focus.debug", FocusAddon.PACKAGE)
    }
}
