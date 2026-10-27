package rocks.chirag.adshield.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

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
}
