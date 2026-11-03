package rocks.chirag.adwizz.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Calendar

/** The dashboard's numbers: per day, per app, per category, saved on the phone. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BlockStatsTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val noon = Calendar.getInstance().apply { set(2026, 10, 3, 12, 0, 0) }.timeInMillis
    private val day = 24 * 60 * 60 * 1000L

    @Before fun setUp() { BlockStats.resetForTest(); java.io.File(ctx.filesDir, "stats.json").delete(); BlockStats.init(ctx) }
    @After fun tearDown() = BlockStats.resetForTest()

    @Test fun countsPerAppAndCategory() {
        BlockStats.record("com.spotify.music", "ads.a.com", BlockCategory.ADS, noon)
        BlockStats.record("com.spotify.music", "t.b.com", BlockCategory.TRACKERS, noon + 10_000)
        BlockStats.record("com.game", "ads.c.com", BlockCategory.ADS, noon + 20_000)
        BlockStats.record("com.game", "ok.com", null, noon + 30_000)
        val s = BlockStats.summary(1, noon)
        assertEquals(2, s.total(BlockCategory.ADS))
        assertEquals(1, s.total(BlockCategory.TRACKERS))
        assertEquals(3, s.blocked)
        assertEquals(4, s.queries)
        assertEquals(setOf("com.spotify.music", "com.game"), s.perApp.map { it.first }.toSet())
        assertEquals(1L, s.perApp.first { it.first == "com.game" }.second[BlockStats.ALLOWED])
    }

    @Test fun aBurstOfLookUpsForOneNameCountsOnce() {
        repeat(3) { BlockStats.record("app", "ads.a.com", BlockCategory.ADS, noon + it * 100L) }
        assertEquals(1, BlockStats.summary(1, noon).total(BlockCategory.ADS))
        BlockStats.record("app", "ads.a.com", BlockCategory.ADS, noon + 60_000)
        assertEquals(2, BlockStats.summary(1, noon).total(BlockCategory.ADS))
    }

    @Test fun unknownAppsAreGrouped() {
        BlockStats.record(null, "ads.a.com", BlockCategory.ADS, noon)
        assertEquals(AppNames.UNKNOWN, BlockStats.summary(1, noon).perApp.single().first)
    }

    @Test fun perDayCoversTheRangeOldestFirst() {
        BlockStats.record("app", "a.com", BlockCategory.ADS, noon - 2 * day)
        BlockStats.record("app", "b.com", BlockCategory.THREATS, noon)
        val s = BlockStats.summary(7, noon)
        assertEquals(7, s.perDay.size)
        assertEquals(20261103, s.perDay.last().first)
        assertEquals(1L, s.perDay[4].second[BlockCategory.ADS.ordinal])
        assertEquals(1L, s.perDay[6].second[BlockCategory.THREATS.ordinal])
        assertEquals(0, BlockStats.summary(1, noon).total(BlockCategory.ADS))   // not today
    }

    @Test fun topDomainsAreSortedByCount() {
        listOf("x.com", "y.com", "y.com", "y.com").forEachIndexed { i, d ->
            BlockStats.record("app$i", d, BlockCategory.ADS, noon)
        }
        assertEquals("y.com" to 3L, BlockStats.summary(1, noon).topDomains.first())
    }

    @Test fun survivesARestart() {
        BlockStats.record("com.game", "ads.c.com", BlockCategory.ADS, noon)
        BlockStats.record("com.game", "x.com", BlockCategory.FIREWALL, noon + 61_000)
        BlockStats.save()
        BlockStats.resetForTest()
        BlockStats.init(ctx)
        val s = BlockStats.summary(1, noon)
        assertEquals(1, s.total(BlockCategory.ADS))
        assertEquals(1, s.total(BlockCategory.FIREWALL))
    }

    @Test fun oldDaysAreDroppedAndBrokenFilesIgnored() {
        val d = StatsData()
        d.add(20260101, "a", "x.com", 0)
        d.add(20261103, "a", "x.com", 0)
        d.prune(20261001)
        assertEquals(0, d.summary(listOf(20260101)).blocked)
        assertEquals(1, StatsData.fromJson(d.toJson()).summary(listOf(20261103)).blocked)
        java.io.File(ctx.filesDir, "stats.json").writeText("{not json")
        BlockStats.resetForTest(); BlockStats.init(ctx)
        assertEquals(0, BlockStats.summary(1, noon).queries)
    }

    @Test fun categoriesFollowTheMatchersLookupOrder() {
        assertNull(BlockCategory.forLookup(rocks.chirag.adwizz.core.DomainMatcher.NOT_BLOCKED))
        assertEquals(BlockCategory.ADS, BlockCategory.forLookup(0))   // the user's own blocks
        assertEquals(BlockCategory.THREATS, BlockCategory.forLookup(1))
        assertEquals(BlockCategory.TRACKERS, BlockCategory.forLookup(2))
        assertEquals(BlockCategory.ADS, BlockCategory.forLookup(3))
        assertEquals(BlockCategory.ADS, BlockCategory.forLookup(rocks.chirag.adwizz.core.DomainMatcher.GENERATED))
    }
}
