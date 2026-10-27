package rocks.chirag.adshield.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.StringReader

/** Reading blocklist files (hosts, plain lists, AdGuard rules) and the matcher built from them. */
class ListParserTest {

    private fun query(name: String, qtype: Int) = DnsFixtures.sampleQuery(name, qtype)


    @Test fun parseLineFormats() {
        assertEquals("ads.com", ListParser.parseLine("0.0.0.0 ads.com"))
        assertEquals("ads.com", ListParser.parseLine("127.0.0.1\tads.com"))
        assertEquals("ads.com", ListParser.parseLine(":: ads.com"))
        assertEquals("ads.com", ListParser.parseLine("ADS.COM."))
        assertEquals("ads.com", ListParser.parseLine("  ads.com  # trailing comment"))
        assertEquals("ads.com", ListParser.parseLine("||ads.com^"))
        assertEquals("under_score.ads.com", ListParser.parseLine("under_score.ads.com"))
    }

    @Test fun parseLineRejects() {
        listOf(
            "", "   ", "# comment", "! adblock comment", "localhost", "0.0.0.0 localhost",
            "0.0.0.0 broadcasthost", "nodot", "||ads.com/path^", "||*ads.com^", "||ads.com\$script",
            "8.8.8.8 notahosts.com", "bad domain.com", "ads.com/", "émoji.com"
        ).forEach { assertNull("should reject: '$it'", ListParser.parseLine(it)) }
    }

    @Test fun parseLineRejectsTooLongDomain() {
        val d = "a".repeat(250) + ".com"
        assertNull(ListParser.parseLine(d))
    }

    @Test fun normaliseCleansUserInput() {
        val out = ListParser.normalise(listOf("Ads.Example.com", "  x.org ", "nonsense", "#c"))
        assertEquals(setOf("ads.example.com", "x.org"), out)
    }

    @Test fun isAllowedChecksParents() {
        val m = DomainMatcher(setOf("ads.com"), setOf("good.ads.com"))
        assertTrue(m.isAllowed("good.ads.com"))
        assertTrue(m.isAllowed("cdn.good.ads.com"))
        assertFalse(m.isAllowed("ads.com"))
        assertFalse(m.isAllowed(""))
        assertFalse(m.isAllowed(null))
    }

    @Test fun emptyAndNullDomainsAreNotBlocked() {
        val m = DomainMatcher(setOf("com"), emptySet())
        assertFalse(m.isBlocked(""))
        assertFalse(m.isBlocked(null))
        assertTrue(m.isBlocked("anything.com"))
    }

    @Test fun parseIntoHandlesMixedListOfManyFormats() {
        val set = HashSet<String>()
        ListParser.parseInto(StringReader("""
            # hosts
            0.0.0.0 a.com
            b.com
            ||c.com^
            ||*.d.com^
            *.e.com
            @@||allowed.com^
            ! comment
        """.trimIndent()), set)
        assertEquals(setOf("a.com", "b.com", "c.com", "d.com", "e.com"), set)
    }

    @Test fun sizeCountsBlockedDomains() {
        assertEquals(2, DomainMatcher(setOf("a.com", "b.com"), setOf("c.com")).size())
    }
}
