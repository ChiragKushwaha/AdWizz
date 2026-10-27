package rocks.chirag.adshield.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SettingsTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private fun prefs() = ctx.getSharedPreferences("adshield", Context.MODE_PRIVATE)

    @Test fun defaults() {
        val s = Settings(ctx)
        assertFalse(s.wantEnabled)
        assertEquals("1.1.1.1", s.primaryDns)
        assertEquals("1.0.0.1", s.secondaryDns)
        assertTrue(s.catchHardcodedDns)
        assertTrue(s.blockTrackers)
        assertTrue(s.blockMalicious)
        assertEquals(Sources.DEFAULT_ENABLED, s.enabledSources)
        assertTrue(s.allowlist.isEmpty())
        assertTrue(s.customBlocklist.isEmpty())
        assertTrue(s.excludedApps.isEmpty())
        assertEquals("system", s.themeMode)
        assertEquals(0L, s.lastListUpdate)
        assertEquals(0L, s.lifetimeBlocked)
    }

    @Test fun valuesPersistAcrossInstances() {
        Settings(ctx).apply {
            wantEnabled = true; primaryDns = "9.9.9.9"; secondaryDns = ""
            catchHardcodedDns = false; blockTrackers = false; blockMalicious = false
            enabledSources = setOf("stevenblack"); allowlist = setOf("ok.com"); customBlocklist = setOf("bad.com")
            excludedApps = setOf("com.bank.app"); themeMode = "dark"; lastListUpdate = 42; lifetimeBlocked = 7
        }
        Settings(ctx).apply {
            assertTrue(wantEnabled)
            assertEquals("9.9.9.9", primaryDns)
            assertEquals("", secondaryDns)
            assertFalse(catchHardcodedDns); assertFalse(blockTrackers); assertFalse(blockMalicious)
            assertEquals(setOf("stevenblack"), enabledSources)
            assertEquals(setOf("ok.com"), allowlist)
            assertEquals(setOf("bad.com"), customBlocklist)
            assertEquals(setOf("com.bank.app"), excludedApps)
            assertEquals("dark", themeMode)
            assertEquals(42L, lastListUpdate)
            assertEquals(7L, lifetimeBlocked)
        }
    }

    @Test fun migrationAddsNewDefaultsAndDropsRemovedLists() {
        // An older install that saved its own source set.
        prefs().edit().putStringSet("sources", setOf("stevenblack", "hagezi_popup")).commit()
        val s = Settings(ctx)
        assertTrue(s.enabledSources.containsAll(Sources.DEFAULT_ENABLED))
        assertFalse("hagezi_popup" in s.enabledSources)
        assertEquals(Sources.REVISION, prefs().getInt("sources_rev", 0))
    }

    @Test fun migrationRunsOnlyOnce() {
        prefs().edit().putStringSet("sources", setOf("stevenblack")).commit()
        Settings(ctx) // migrates
        Settings(ctx).enabledSources = setOf("adguard_dns") // user turns things off afterwards
        assertEquals(setOf("adguard_dns"), Settings(ctx).enabledSources)
    }

    @Test fun freshInstallJustRecordsRevision() {
        Settings(ctx)
        assertFalse(prefs().contains("sources"))
        assertEquals(Sources.REVISION, prefs().getInt("sources_rev", 0))
    }

    @Test fun returnedSetsAreCopies() {
        val s = Settings(ctx)
        s.allowlist = setOf("a.com")
        val copy = s.allowlist.toMutableSet().apply { add("b.com") }
        assertEquals(setOf("a.com"), s.allowlist)
        assertEquals(2, copy.size)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BlocklistRepositoryTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private lateinit var settings: Settings
    private lateinit var repo: BlocklistRepository
    private val listDir get() = File(ctx.filesDir, "lists")

    @Before fun setUp() {
        listDir.deleteRecursively()
        settings = Settings(ctx)
        repo = BlocklistRepository(ctx)
    }

    private fun writeList(id: String, body: String) = File(listDir.apply { mkdirs() }, "$id.txt").writeText(body)

    @Test fun activeSourcesFollowSwitches() {
        settings.enabledSources = setOf("stevenblack")
        settings.blockTrackers = false; settings.blockMalicious = false; settings.catchHardcodedDns = false
        assertEquals(listOf("stevenblack"), repo.activeSources().map { it.id })

        settings.blockTrackers = true
        assertTrue(repo.activeSources().any { it.group == SourceGroup.TRACKERS })
        assertTrue(repo.activeSources().filter { it.group == SourceGroup.TRACKERS }.size ==
            Sources.ALL.count { it.group == SourceGroup.TRACKERS })

        settings.blockMalicious = true
        assertTrue(repo.activeSources().any { it.group == SourceGroup.SECURITY })
        settings.catchHardcodedDns = true
        assertTrue(repo.activeSources().any { it.group == SourceGroup.BYPASS })

        settings.enabledSources = emptySet()
        assertTrue(repo.activeSources().none { it.group == SourceGroup.ADS })
    }

    @Test fun starterListWorksOffline() {
        val m = repo.buildMatcher()
        assertTrue(m.isBlocked("doubleclick.net"))
        assertTrue(m.isBlocked("pagead2.googlesyndication.com"))
        assertTrue(m.isBlocked("popads.net"))
        assertTrue(m.isBlocked("sub.onclickads.net"))
        assertTrue(m.isBlocked("dns.google"))
        assertFalse(m.isBlocked("example.com"))
        assertFalse(m.isBlocked("google.com"))
        assertTrue(m.size() > 150)
    }

    @Test fun trackerStarterListFollowsSwitch() {
        settings.blockTrackers = true
        assertTrue(repo.buildMatcher().isBlocked("google-analytics.com"))
        settings.blockTrackers = false
        assertFalse(repo.buildMatcher().isBlocked("scorecardresearch.com"))
        // Ads stay blocked either way.
        assertTrue(repo.buildMatcher().isBlocked("doubleclick.net"))
    }

    @Test fun downloadedListsAreLoadedOnlyWhenActive() {
        writeList("stevenblack", "0.0.0.0 from-steven.test\n")
        writeList("easyprivacy", "0.0.0.0 from-privacy.test\n")
        writeList("nocoin", "0.0.0.0 from-nocoin.test\n")
        settings.enabledSources = setOf("stevenblack")
        settings.blockTrackers = true
        settings.blockMalicious = false
        val m = repo.buildMatcher()
        assertTrue(m.isBlocked("from-steven.test"))
        assertTrue(m.isBlocked("from-privacy.test"))
        assertFalse(m.isBlocked("from-nocoin.test"))
    }

    @Test fun customBlocksAndAllowlistWins() {
        settings.customBlocklist = setOf("my-annoying.site")
        settings.allowlist = setOf("doubleclick.net")
        val m = repo.buildMatcher()
        assertTrue(m.isBlocked("my-annoying.site"))
        assertTrue(m.isBlocked("cdn.my-annoying.site"))
        assertFalse(m.isBlocked("doubleclick.net"))
        assertFalse(m.isBlocked("ad.doubleclick.net"))
    }

    @Test fun corruptListFileIsIgnored() {
        listDir.mkdirs()
        File(listDir, "stevenblack.txt").writeBytes(byteArrayOf(0, -1, -2, 10, 0x61))
        settings.enabledSources = setOf("stevenblack")
        assertTrue(repo.buildMatcher().isBlocked("doubleclick.net"))
    }

    @Test fun buildMatcherPublishesListSize() {
        val m = repo.buildMatcher()
        assertEquals(m.size(), VpnState.stats.value.listSize)
    }

    @Test fun needsDownloadWhenListMissing() {
        settings.lastListUpdate = System.currentTimeMillis()
        assertTrue(repo.needsDownload())
    }

    @Test fun needsDownloadWhenStale() {
        repo.activeSources().forEach { writeList(it.id, "0.0.0.0 x.test\n") }
        settings.lastListUpdate = System.currentTimeMillis() - 3L * 24 * 3600 * 1000
        assertTrue(repo.needsDownload())
    }

    @Test fun noDownloadWhenFreshAndComplete() {
        repo.activeSources().forEach { writeList(it.id, "0.0.0.0 x.test\n") }
        settings.lastListUpdate = System.currentTimeMillis()
        assertFalse(repo.needsDownload())
    }

    @Test fun emptyListFileCountsAsMissing() {
        repo.activeSources().forEach { writeList(it.id, "0.0.0.0 x.test\n") }
        writeList(repo.activeSources().first().id, "")
        settings.lastListUpdate = System.currentTimeMillis()
        assertTrue(repo.needsDownload())
    }
}

class VpnStateTest {
    @Before fun reset() {
        VpnState.setStatus(Status.STOPPED)
        VpnState.resetSession()
    }

    @Test fun recordsCounts() {
        VpnState.record("a.com", true)
        VpnState.record("b.com", false)
        VpnState.record("c.com", true)
        assertEquals(3, VpnState.stats.value.queries)
        assertEquals(2, VpnState.stats.value.blocked)
        assertEquals(listOf("c.com", "b.com", "a.com"), VpnState.log.value.map { it.domain })
    }

    @Test fun collapsesImmediateRepeatsButStillCounts() {
        repeat(3) { VpnState.record("same.com", true) }
        assertEquals(1, VpnState.log.value.size)
        assertEquals(3, VpnState.stats.value.queries)
        VpnState.record("same.com", false) // different outcome: new row
        assertEquals(2, VpnState.log.value.size)
    }

    @Test fun logIsCapped() {
        repeat(400) { VpnState.record("d$it.com", false) }
        assertEquals(300, VpnState.log.value.size)
        assertEquals("d399.com", VpnState.log.value.first().domain)
    }

    @Test fun resetSessionClearsLogAndCounts() {
        VpnState.record("a.com", true)
        VpnState.resetSession()
        assertEquals(0, VpnState.stats.value.queries)
        assertTrue(VpnState.log.value.isEmpty())
    }

    @Test fun statusAndError() {
        VpnState.setStatus(Status.ERROR, "boom")
        assertEquals(Status.ERROR, VpnState.status.value)
        assertEquals("boom", VpnState.error.value)
        VpnState.setStatus(Status.RUNNING)
        assertNull(VpnState.error.value)
    }

    @Test fun updatingFlag() {
        VpnState.setUpdating(true)
        assertTrue(VpnState.updatingLists.value)
        VpnState.setUpdating(false)
        assertFalse(VpnState.updatingLists.value)
    }
}
