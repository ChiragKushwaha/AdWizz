package rocks.chirag.adshield.core

import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader

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
        DomainMatcher.parseInto(StringReader(text), hs)
        val ds = DomainSet()
        DomainMatcher.parseInto(StringReader(text), ds)
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

    /** Real-world scale: ~800k domains must fit in a small heap and look up fast. */
    @Test fun scaleMemoryAndSpeed() {
        val n = 800_000
        val s = DomainSet(1_000_000)
        for (i in 0 until n) s.add("tracker-$i.ad-network-${i % 997}.com")
        s.trim()
        assertEquals(n, s.size())
        // ~41 bytes/domain of text + table; a HashSet<String> would need ~120+ bytes per entry.
        val perDomain = s.memoryBytes().toDouble() / n
        assertTrue("bytes per domain = $perDomain", perDomain < 60)

        val m = DomainMatcher(s, DomainSet())
        val t0 = System.nanoTime()
        var hits = 0
        for (i in 0 until 1_000_000) {
            if (m.isBlocked("cdn.img.tracker-${i % n}.ad-network-${(i % n) % 997}.com")) hits++
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(1_000_000, hits)
        assertTrue("1M lookups took ${ms}ms", ms < 5_000)
    }

    @Test fun parseLineIsFastOnHugeLists() {
        val sb = StringBuilder()
        repeat(300_000) { sb.append("0.0.0.0 host-").append(it).append(".example.org\n") }
        val s = DomainSet(300_000)
        val t0 = System.nanoTime()
        DomainMatcher.parseInto(StringReader(sb.toString()), s)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(300_000, s.size())
        assertTrue("parse took ${ms}ms", ms < 5_000)
    }
}

class GeneratedDomainTest {
    @Test fun catchesRotatingAdDomains() {
        listOf("www.pgjlctmswgnwf.com", "pgjlctmswgnwf.com", "cdn.x.qdlcqrmjjwx.com", "bcdfghjklmnp.co.uk")
            .forEach { assertTrue(it, GeneratedDomain.looksGenerated(it)) }
    }

    /** Real sites that looser rules wrongly flagged during tuning; these must never be blocked. */
    @Test fun leavesRealSitesAlone() {
        listOf(
            "letsencrypt.org", "crunchyrollcdn.com", "healthychildren.org", "simplypsychology.org",
            "smythstoys.com", "msftstatic.com", "skyscrapercity.com", "psychcentral.com", "plentysystems.com",
            "linksyssmartwifi.com", "d3cod80thn7qnd.cloudfront.net", "rbxcdn.com", "crwdcntrl.net",
            "google.com", "x.com", "bbc.co.uk", "9ffyu1g9c.com", "cdnhwctnm107.com", "",
        ).forEach { assertFalse(it, GeneratedDomain.looksGenerated(it)) }
        assertFalse(GeneratedDomain.looksGenerated(null))
    }

    @Test fun siteLabel() {
        assertEquals("example", GeneratedDomain.siteLabel("a.b.example.com"))
        assertEquals("example", GeneratedDomain.siteLabel("ads.example.co.uk"))
        assertEquals("example", GeneratedDomain.siteLabel("example.de"))
        assertNull(GeneratedDomain.siteLabel("localhost"))
    }

    @Test fun matcherUsesItOnlyWhenEnabledAndAllowlistWins() {
        val off = DomainMatcher(emptySet(), emptySet())
        assertFalse(off.isBlocked("www.pgjlctmswgnwf.com"))
        val on = DomainMatcher(emptySet(), setOf("pgjlctmswgnwf.com")).withGeneratedDomainBlocking(true)
        assertFalse(on.isBlocked("www.pgjlctmswgnwf.com")) // allowlisted
        assertTrue(DomainMatcher(emptySet(), emptySet()).withGeneratedDomainBlocking(true).isBlocked("www.pgjlctmswgnwf.com"))
    }
}
