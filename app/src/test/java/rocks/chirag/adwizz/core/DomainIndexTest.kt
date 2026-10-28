package rocks.chirag.adwizz.core

import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** The on-device blocklist database: a memory-mapped file of sorted domain fingerprints. */
class DomainIndexTest {
    private val dir: File = Files.createTempDirectory("idx").toFile()
    private val file = File(dir, "blocked.idx")

    @After fun clean() { dir.deleteRecursively() }

    private fun build(vararg domains: String): DomainIndex {
        val b = DomainIndex.Builder(16)
        domains.forEach(b::add)
        b.writeTo(file)
        return DomainIndex.open(file)
    }

    @Test fun findsWhatWasAddedAndNothingElse() {
        val idx = build("ads.example.com", "tracker.net")
        assertTrue(idx.contains("ads.example.com", 0))
        assertTrue(idx.contains("tracker.net", 0))
        assertFalse(idx.contains("example.com", 0))
        assertFalse(idx.contains("news.example.com", 0))
        assertEquals(2, idx.size())
    }

    @Test fun looksUpASuffixWithoutCopying() {
        val idx = build("example.com")
        assertTrue(idx.contains("ads.example.com", 4))
        assertFalse(idx.contains("ads.example.com", 0))
    }

    @Test fun duplicatesAreStoredOnce() {
        assertEquals(1, build("a.com", "a.com", "a.com").size())
    }

    @Test fun emptyIndexIsValid() {
        val idx = build()
        assertEquals(0, idx.size())
        assertFalse(idx.contains("a.com", 0))
    }

    @Test fun blocksSubdomainsThroughTheMatcher() {
        val m = DomainMatcher(arrayOf<DomainLookup>(build("doubleclick.net")), DomainSet())
        assertTrue(m.isBlocked("doubleclick.net"))
        assertTrue(m.isBlocked("googleads.g.doubleclick.net"))
        assertFalse(m.isBlocked("doubleclick.net.example.org"))
    }

    @Test fun allowlistAndExtraListsStillWork() {
        val extra = DomainSet().apply { add("custom.com") }
        val allowed = DomainSet().apply { add("ok.ads.com") }
        val m = DomainMatcher(arrayOf<DomainLookup>(build("ads.com"), extra), allowed)
        assertTrue(m.isBlocked("x.ads.com"))
        assertFalse(m.isBlocked("ok.ads.com"))
        assertTrue(m.isBlocked("custom.com"))
        assertEquals(2, m.size())
    }

    @Test fun rejectsFilesThatArentACompleteIndex() {
        file.writeText("0.0.0.0 not-an-index.com\n")
        assertThrows(java.io.IOException::class.java) { DomainIndex.open(file) }
        build("a.com", "b.com")
        file.writeBytes(file.readBytes().copyOf(file.length().toInt() - 3))   // cut off mid-entry
        assertThrows(java.io.IOException::class.java) { DomainIndex.open(file) }
    }

    @Test fun leavesNoTempFileBehind() {
        build("a.com")
        assertFalse(File(dir, "blocked.idx.tmp").exists())
        assertEquals(16L + 8, file.length())   // header + one 8-byte fingerprint
    }

    @Test fun millionsOfDomainsWithNoFalseMatches() {
        val n = 1_000_000
        val b = DomainIndex.Builder(n)
        for (i in 0 until n) b.add("ad$i.example$i.com")
        assertEquals(n, b.writeTo(file))
        val idx = DomainIndex.open(file)
        for (i in 0 until n step 997) assertTrue(idx.contains("ad$i.example$i.com", 0))
        val falseHits = (0 until 200_000).count { idx.contains("site$it.example.org", 0) }
        assertEquals(0, falseHits)
        assertEquals(16L + 8L * n, file.length())   // 8 bytes per domain on disk, nothing on the heap
    }
}
