package rocks.chirag.adwizz.core

import java.io.StringReader
import org.junit.Assert.*
import org.junit.Test

/** Real-world scale: ~800k domains must fit in a small heap and load and look up fast. */
class DomainSetScaleTest {

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
        ListParser.parseInto(StringReader(sb.toString()), s)
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertEquals(300_000, s.size())
        assertTrue("parse took ${ms}ms", ms < 5_000)
    }
}
