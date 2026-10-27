package rocks.chirag.adshield.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.StringReader

/** More edge cases for the packet, DNS and blocklist code that every query goes through. */
class CoreExtraTest {

    private fun query(name: String, qtype: Int) = CoreTest.sampleQuery(name, qtype)

    // ---------- DnsMessage ----------

    @Test fun blockedAaaaAnswerIs16ZeroBytes() {
        val q = query("ads.example.com", DnsMessage.TYPE_AAAA)
        val dq = DnsMessage.parseQuery(q)!!
        val r = DnsMessage.blockedResponse(q, dq)
        assertEquals(1, IpPacket.u16(r, 6))
        assertEquals(dq.questionEnd + 12 + 16, r.size)
        assertTrue(r.copyOfRange(r.size - 16, r.size).all { it == 0.toByte() })
        assertEquals(DnsMessage.TYPE_AAAA, IpPacket.u16(r, dq.questionEnd + 2))
    }

    @Test fun blockedHttpsRecordGetsEmptyNoError() {
        val q = query("ads.example.com", DnsMessage.TYPE_HTTPS)
        val dq = DnsMessage.parseQuery(q)!!
        val r = DnsMessage.blockedResponse(q, dq)
        assertEquals(0, IpPacket.u16(r, 6))       // no answers
        assertEquals(0, r[3].toInt() and 0x0F)    // NOERROR
        assertEquals(dq.questionEnd, r.size)
    }

    @Test fun blockedResponseKeepsIdAndRecursionFlag() {
        val q = query("x.com", 1)
        val r = DnsMessage.blockedResponse(q, DnsMessage.parseQuery(q)!!)
        assertEquals(0x1234, IpPacket.u16(r, 0))
        assertTrue(r[2].toInt() and 0x01 != 0) // RD copied
        assertTrue(r[3].toInt() and 0x80 != 0) // RA set
    }

    @Test fun servFailHasRcode2AndNoRecords() {
        val q = query("x.com", 1)
        val r = DnsMessage.servFail(q, DnsMessage.parseQuery(q)!!)
        assertEquals(2, r[3].toInt() and 0x0F)
        assertEquals(1, IpPacket.u16(r, 4))
        assertEquals(0, IpPacket.u16(r, 6))
        assertEquals(0, IpPacket.u16(r, 10))
    }

    @Test fun parseQueryRejectsCompressionAndOverlongLabels() {
        val q = query("a.com", 1)
        q[12] = 0xC0.toByte()
        assertNull(DnsMessage.parseQuery(q))
        val long = query("a.com", 1)
        long[12] = 64
        assertNull(DnsMessage.parseQuery(long))
    }

    @Test fun parseQueryRejectsNonQueryOpcodeAndNoQuestions() {
        val q = query("a.com", 1)
        q[2] = (q[2].toInt() or 0x28).toByte() // opcode 5 (UPDATE)
        assertNull(DnsMessage.parseQuery(q))
        val empty = query("a.com", 1)
        empty[5] = 0
        assertNull(DnsMessage.parseQuery(empty))
    }

    @Test fun parseQueryRejectsTruncatedQuestion() {
        val q = query("example.com", 1)
        assertNull(DnsMessage.parseQuery(q.copyOf(16)))
    }

    @Test fun readNameSurvivesPointerLoop() {
        // Header + a name that points at itself.
        val b = ByteArray(14)
        b[12] = 0xC0.toByte(); b[13] = 12
        assertNull(DnsMessage.readName(b, intArrayOf(12)))
    }

    @Test fun cnameTargetsHandlesTruncatedAnswer() {
        val q = query("a.com", 1)
        val dq = DnsMessage.parseQuery(q)!!
        val r = q.copyOf(dq.questionEnd + 4)
        r[7] = 1 // claims one answer, but it's cut off
        assertTrue(DnsMessage.cnameTargets(r).isEmpty())
        assertTrue(DnsMessage.cnameTargets(null).isEmpty())
    }

    @Test fun cnameTargetsIgnoresARecords() {
        val q = query("a.com", 1)
        val dq = DnsMessage.parseQuery(q)!!
        val o = ByteArrayOutputStream()
        val head = q.copyOf(dq.questionEnd)
        head[7] = 1; head[11] = 0
        o.write(head)
        o.write(byteArrayOf(0xC0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, 1, 2, 3, 4))
        assertTrue(DnsMessage.cnameTargets(o.toByteArray()).isEmpty())
    }

    // ---------- DomainMatcher ----------

    @Test fun parseLineFormats() {
        assertEquals("ads.com", DomainMatcher.parseLine("0.0.0.0 ads.com"))
        assertEquals("ads.com", DomainMatcher.parseLine("127.0.0.1\tads.com"))
        assertEquals("ads.com", DomainMatcher.parseLine(":: ads.com"))
        assertEquals("ads.com", DomainMatcher.parseLine("ADS.COM."))
        assertEquals("ads.com", DomainMatcher.parseLine("  ads.com  # trailing comment"))
        assertEquals("ads.com", DomainMatcher.parseLine("||ads.com^"))
        assertEquals("under_score.ads.com", DomainMatcher.parseLine("under_score.ads.com"))
    }

    @Test fun parseLineRejects() {
        listOf(
            "", "   ", "# comment", "! adblock comment", "localhost", "0.0.0.0 localhost",
            "0.0.0.0 broadcasthost", "nodot", "||ads.com/path^", "||*ads.com^", "||ads.com\$script",
            "8.8.8.8 notahosts.com", "bad domain.com", "ads.com/", "émoji.com"
        ).forEach { assertNull("should reject: '$it'", DomainMatcher.parseLine(it)) }
    }

    @Test fun parseLineRejectsTooLongDomain() {
        val d = "a".repeat(250) + ".com"
        assertNull(DomainMatcher.parseLine(d))
    }

    @Test fun normaliseCleansUserInput() {
        val out = DomainMatcher.normalise(listOf("Ads.Example.com", "  x.org ", "nonsense", "#c"))
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
        DomainMatcher.parseInto(StringReader("""
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

    // ---------- IpPacket ----------

    @Test fun icmpUnreachableForIpv4SwapsAddressesAndHasValidChecksums() {
        val udp = IpPacket.buildUdp(byteArrayOf(10, 0, 0, 2), byteArrayOf(8, 8, 8, 8), 5555, 853, ByteArray(10))
        val icmp = IpPacket.buildIcmpUnreachable(udp, udp.size)
        val p = IpPacket.parse(icmp, icmp.size)!!
        assertEquals(IpPacket.PROTO_ICMP, p.protocol)
        assertArrayEquals(byteArrayOf(8, 8, 8, 8), p.srcAddr)
        assertArrayEquals(byteArrayOf(10, 0, 0, 2), p.dstAddr)
        assertEquals(3, icmp[20].toInt())   // destination unreachable
        assertEquals(13, icmp[21].toInt())  // administratively prohibited
        assertEquals(0, IpPacket.checksum(icmp, 0, 20, 0))
        assertEquals(0, IpPacket.checksum(icmp, 20, icmp.size - 20, 0))
    }

    @Test fun icmpUnreachableForIpv6() {
        val src = ByteArray(16).also { it[0] = 0xfd.toByte(); it[15] = 2 }
        val dst = ByteArray(16).also { it[0] = 0x20; it[15] = 1 }
        val udp = IpPacket.buildUdp(src, dst, 5555, 443, ByteArray(10))
        val icmp = IpPacket.buildIcmpUnreachable(udp, udp.size)
        val p = IpPacket.parse(icmp, icmp.size)!!
        assertEquals(6, p.version)
        assertEquals(IpPacket.PROTO_ICMPV6, p.protocol)
        assertArrayEquals(dst, p.srcAddr)
        assertArrayEquals(src, p.dstAddr)
        assertEquals(1, icmp[40].toInt())
    }

    @Test fun fragmentedIpv4IsNotTreatedAsUdp() {
        val pkt = IpPacket.buildUdp(byteArrayOf(10, 0, 0, 2), byteArrayOf(10, 0, 0, 1), 1, 53, ByteArray(4))
        pkt[6] = 0x20 // more-fragments
        val p = IpPacket.parse(pkt, pkt.size)!!
        assertEquals(-1, p.dstPort)
    }

    @Test fun rejectsBadLengthsAndVersions() {
        val pkt = IpPacket.buildUdp(byteArrayOf(10, 0, 0, 2), byteArrayOf(10, 0, 0, 1), 1, 53, ByteArray(4))
        assertNull(IpPacket.parse(pkt, pkt.size - 1)) // total length > bytes read
        val v5 = pkt.copyOf(); v5[0] = 0x55
        assertNull(IpPacket.parse(v5, v5.size))
        assertNull(IpPacket.parse(ByteArray(0), 0))
        assertNull(IpPacket.parse(ByteArray(30).also { it[0] = 0x60 }, 30))
    }

    @Test fun udpChecksumIsValidForIpv4() {
        val pkt = IpPacket.buildUdp(byteArrayOf(10, 0, 0, 1), byteArrayOf(10, 0, 0, 2), 53, 4000, ByteArray(7) { it.toByte() })
        // Pseudo-header sum + UDP segment must fold to zero.
        var sum = 0L
        for (i in 12 until 20 step 2) sum += IpPacket.u16(pkt, i)
        sum += IpPacket.PROTO_UDP + (pkt.size - 20)
        assertEquals(0, IpPacket.checksum(pkt, 20, pkt.size - 20, sum))
    }
}
