package rocks.chirag.adwizz.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The on-device database is rebuilt only when the lists behind it change. */
@RunWith(AndroidJUnit4::class)   // android.util.Log
@Config(sdk = [35])
class BlocklistIndexTest {
    private val dir: File = Files.createTempDirectory("lists").toFile()

    @After fun clean() { dir.deleteRecursively() }

    private fun list(id: String, vararg domains: String): BlocklistIndex.Input {
        val f = File(dir, "$id.txt").apply { writeText(domains.joinToString("\n") { "0.0.0.0 $it" }) }
        return BlocklistIndex.Input(id, BlocklistIndex.version(f)) { f.reader() }
    }

    @Test fun buildsFromEveryList() {
        val idx = BlocklistIndex(dir).get(listOf(list("a", "ads.com"), list("b", "track.net", "ads.com")))
        assertTrue(idx.contains("ads.com", 0))
        assertTrue(idx.contains("track.net", 0))
        assertEquals(2, idx.size())
    }

    @Test fun reusesTheDatabaseWhileNothingChanged() {
        val index = BlocklistIndex(dir)
        val inputs = listOf(list("a", "ads.com"))
        val first = index.get(inputs)
        assertSame(first, index.get(inputs))
        assertEquals(1, index.builds)
    }

    @Test fun reopensTheSavedDatabaseAfterARestart() {
        val inputs = listOf(list("a", "ads.com"))
        BlocklistIndex(dir).get(inputs)
        val afterRestart = BlocklistIndex(dir)
        assertTrue(afterRestart.get(inputs).contains("ads.com", 0))
        assertEquals(0, afterRestart.builds)   // opened, not rebuilt
    }

    @Test fun rebuildsWhenAListIsDownloadedAgain() {
        val index = BlocklistIndex(dir)
        index.get(listOf(list("a", "ads.com")))
        File(dir, "a.txt").setLastModified(1_000)   // pretend an older copy, then re-download
        val updated = list("a", "ads.com", "new-ad.com")
        assertTrue(index.get(listOf(updated)).contains("new-ad.com", 0))
        assertEquals(2, index.builds)
    }

    @Test fun rebuildsWhenAListIsSwitchedOnOrOff() {
        val index = BlocklistIndex(dir)
        val a = list("a", "ads.com"); val b = list("b", "track.net")
        assertFalse(index.get(listOf(a)).contains("track.net", 0))
        assertTrue(index.get(listOf(a, b)).contains("track.net", 0))
        assertFalse(index.get(listOf(a)).contains("track.net", 0))
        assertEquals(3, index.builds)
    }

    @Test fun rebuildsADamagedDatabase() {
        val inputs = listOf(list("a", "ads.com"))
        BlocklistIndex(dir).get(inputs)
        File(dir, "blocked.idx").writeText("garbage")
        val index = BlocklistIndex(dir)
        assertTrue(index.get(inputs).contains("ads.com", 0))
        assertEquals(1, index.builds)
    }

    @Test fun anUnreadableListIsSkippedNotFatal() {
        val broken = BlocklistIndex.Input("gone", "1@1") { throw java.io.FileNotFoundException("gone") }
        val idx = BlocklistIndex(dir).get(listOf(broken, list("a", "ads.com")))
        assertTrue(idx.contains("ads.com", 0))
    }
}
