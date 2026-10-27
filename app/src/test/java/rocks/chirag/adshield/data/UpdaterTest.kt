package rocks.chirag.adshield.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** The update manifest must only ever point the app at our own, verifiable GitHub release. */
@RunWith(AndroidJUnit4::class)   // org.json needs the Android runtime
@Config(sdk = [35])
class UpdaterTest {
    private val sha = "a".repeat(64)
    private val url = "https://github.com/ChiragKushwaha/AdWizz/releases/download/v1.1.0/AdWizz.apk"

    private fun manifest(
        versionCode: Any = 2, apkUrl: String = url, sha256: String = sha, size: Long = 1_200_000, notes: String = "Fixes",
    ) = """{"versionCode":$versionCode,"versionName":"1.1.0","apkUrl":"$apkUrl","sha256":"$sha256","sizeBytes":$size,"notes":"$notes"}"""

    @Test fun parsesAValidManifest() {
        val info = UpdateManifest.parse(manifest())!!
        assertEquals(2, info.versionCode)
        assertEquals("1.1.0", info.versionName)
        assertEquals(url, info.apkUrl)
        assertEquals(sha, info.sha256)
        assertEquals("Fixes", info.notes)
    }

    @Test fun rejectsDownloadsFromAnywhereElse() {
        listOf(
            "http://github.com/ChiragKushwaha/AdWizz/releases/download/v1/AdWizz.apk",   // not HTTPS
            "https://evil.example/AdWizz.apk",
            "https://github.com/SomeoneElse/AdWizz/releases/download/v1/AdWizz.apk",
            "https://github.com/ChiragKushwaha/AdWizz.evil.com/releases/download/x.apk",
        ).forEach { assertNull(it, UpdateManifest.parse(manifest(apkUrl = it))) }
    }

    @Test fun rejectsBadChecksumsSizesAndVersions() {
        assertNull(UpdateManifest.parse(manifest(sha256 = "xyz")))
        assertNull(UpdateManifest.parse(manifest(sha256 = "a".repeat(63))))
        assertNull(UpdateManifest.parse(manifest(size = 999L * 1024 * 1024)))
        assertNull(UpdateManifest.parse(manifest(versionCode = 0)))
        assertNull(UpdateManifest.parse(manifest(versionCode = "\"two\"")))
        assertNull(UpdateManifest.parse("not json"))
        assertNull(UpdateManifest.parse("{}"))
    }

    @Test fun uppercaseChecksumIsNormalised() {
        assertEquals(sha, UpdateManifest.parse(manifest(sha256 = "A".repeat(64)))!!.sha256)
    }

    @Test fun onlyHigherVersionCodesAreUpdates() {
        val info = UpdateManifest.parse(manifest(versionCode = 5))!!
        assertTrue(Updater.isNewer(info, current = 4))
        assertFalse(Updater.isNewer(info, current = 5))
        assertFalse(Updater.isNewer(info, current = 6))
    }

    @Test fun sha256OfAFile() {
        val f = File.createTempFile("adwizz", ".bin").apply { writeText("abc"); deleteOnExit() }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdateDownload.sha256(f))
    }

    @Test fun everyStateExposesItsUpdate() {
        val info = UpdateManifest.parse(manifest())!!
        assertNull(UpdateState.Idle.info)
        listOf(
            UpdateState.Available(info), UpdateState.Downloading(info, 0.5f), UpdateState.Ready(info, File("x")),
            UpdateState.Installing(info), UpdateState.Failed(info, "x"),
        ).forEach { assertEquals(info, it.info) }
    }
}
