package rocks.chirag.adshield.core

import java.io.StringReader
import org.junit.Assert.*
import org.junit.Test

class DomainSetTest {

    @Test fun addAndContains() {
        val s = DomainSet()
        assertTrue(s.add("ads.example.com"))
        assertFalse(s.add("ads.example.com")) // duplicate
        assertEquals(1, s.size())
        assertTrue(s.contains("ads.example.com"))
        assertFalse(s.contains("example.com"))
        assertFalse(s.contains("ads.example.co"))
        assertFalse(s.contains("ads.example.comm"))
    }

    @Test fun containsWithOffsetMatchesSuffixWithoutSubstring() {
        val s = DomainSet().apply { add("example.com") }
        assertTrue(s.contains("ads.example.com", 4))
        assertFalse(s.contains("ads.example.com", 3))
        assertFalse(s.contains("ads.example.com", 15)) // empty
        assertFalse(s.contains("ads.example.com", 99))
    }

    @Test fun rejectsEmptyAndTooLong() {
        val s = DomainSet()
        assertFalse(s.add(""))
        assertFalse(s.add("a".repeat(256)))
        assertTrue(s.add("a".repeat(255)))
        assertTrue(s.contains("a".repeat(255)))
    }

    @Test fun emptySetContainsNothing() {
        assertFalse(DomainSet().contains("a.com"))
        assertEquals(0, DomainSet().size())
    }

    @Test fun nonAsciiQueriesNeverMatch() {
        val s = DomainSet().apply { add("xn--bcher-kva.com") }
        assertFalse(s.contains("bücher.com"))
        // Same length, and U+0178's low byte equals 'x': must not collide.
        assertFalse(s.contains("\u0178n--bcher-kva.com"))
    }

    @Test fun growsPastInitialCapacityAndKeepsEverything() {
        val s = DomainSet(4)
        repeat(50_000) { s.add("host$it.example.net") }
        assertEquals(50_000, s.size())
        repeat(50_000) { assertTrue(s.contains("host$it.example.net")) }
        assertFalse(s.contains("host50000.example.net"))
    }

    @Test fun trimKeepsContents() {
        val s = DomainSet(10_000)
        repeat(100) { s.add("d$it.org") }
        val before = s.memoryBytes()
        s.trim()
        assertTrue(s.memoryBytes() < before)
        repeat(100) { assertTrue(s.contains("d$it.org")) }
        assertTrue(s.add("after-trim.org"))
        assertTrue(s.contains("after-trim.org"))
    }

    @Test fun parseIntoDomainSetMatchesSetVersion() {
        val text = "0.0.0.0 a.com\nb.com\n||c.com^\n# x\n*.d.com\n127.0.0.1   e.com # c\n"
        val hs = HashSet<String>()
        ListParser.parseInto(StringReader(text), hs)
        val ds = DomainSet()
        ListParser.parseInto(StringReader(text), ds)
        assertEquals(hs.size, ds.size())
        hs.forEach { assertTrue(it, ds.contains(it)) }
    }

    @Test fun matcherWithDomainSetsBehavesLikeBefore() {
        val blocked = DomainSet().apply { add("doubleclick.net"); add("ads.tiktok.com") }
        val allowed = DomainSet().apply { add("ok.doubleclick.net") }
        val m = DomainMatcher(blocked, allowed)
        assertTrue(m.isBlocked("doubleclick.net"))
        assertTrue(m.isBlocked("a.b.c.doubleclick.net"))
        assertFalse(m.isBlocked("ok.doubleclick.net"))
        assertFalse(m.isBlocked("x.ok.doubleclick.net"))
        assertFalse(m.isBlocked("tiktok.com"))
        assertFalse(m.isBlocked("notdoubleclick.net"))
        assertFalse(m.isBlocked("doubleclick.net."))
    }
}
