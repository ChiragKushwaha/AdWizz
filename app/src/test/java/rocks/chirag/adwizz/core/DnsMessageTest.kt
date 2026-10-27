package rocks.chirag.adwizz.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.StringReader

/** Building and parsing DNS messages, including hostile/truncated input. */
class DnsMessageTest {

    private fun query(name: String, qtype: Int) = DnsFixtures.sampleQuery(name, qtype)


    @Test fun blockedAaaaAnswerIs16ZeroBytes() {
        val q = query("ads.example.com", DnsMessage.TYPE_AAAA)
        val dq = DnsMessage.parseQuery(q)!!
        val r = DnsMessage.blockedResponse(q, dq)
        assertEquals(1, Bytes.u16(r, 6))
        assertEquals(dq.questionEnd + 12 + 16, r.size)
        assertTrue(r.copyOfRange(r.size - 16, r.size).all { it == 0.toByte() })
        assertEquals(DnsMessage.TYPE_AAAA, Bytes.u16(r, dq.questionEnd + 2))
    }

    @Test fun blockedHttpsRecordGetsEmptyNoError() {
        val q = query("ads.example.com", DnsMessage.TYPE_HTTPS)
        val dq = DnsMessage.parseQuery(q)!!
        val r = DnsMessage.blockedResponse(q, dq)
        assertEquals(0, Bytes.u16(r, 6))       // no answers
        assertEquals(0, r[3].toInt() and 0x0F)    // NOERROR
        assertEquals(dq.questionEnd, r.size)
    }

    @Test fun blockedResponseKeepsIdAndRecursionFlag() {
        val q = query("x.com", 1)
        val r = DnsMessage.blockedResponse(q, DnsMessage.parseQuery(q)!!)
        assertEquals(0x1234, Bytes.u16(r, 0))
        assertTrue(r[2].toInt() and 0x01 != 0) // RD copied
        assertTrue(r[3].toInt() and 0x80 != 0) // RA set
    }

    @Test fun servFailHasRcode2AndNoRecords() {
        val q = query("x.com", 1)
        val r = DnsMessage.servFail(q, DnsMessage.parseQuery(q)!!)
        assertEquals(2, r[3].toInt() and 0x0F)
        assertEquals(1, Bytes.u16(r, 4))
        assertEquals(0, Bytes.u16(r, 6))
        assertEquals(0, Bytes.u16(r, 10))
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
        assertNull(DnsResponse.readName(b, intArrayOf(12)))
    }

    @Test fun cnameTargetsHandlesTruncatedAnswer() {
        val q = query("a.com", 1)
        val dq = DnsMessage.parseQuery(q)!!
        val r = q.copyOf(dq.questionEnd + 4)
        r[7] = 1 // claims one answer, but it's cut off
        assertTrue(DnsResponse.cnameTargets(r).isEmpty())
        assertTrue(DnsResponse.cnameTargets(null).isEmpty())
    }

    @Test fun cnameTargetsIgnoresARecords() {
        val q = query("a.com", 1)
        val dq = DnsMessage.parseQuery(q)!!
        val o = ByteArrayOutputStream()
        val head = q.copyOf(dq.questionEnd)
        head[7] = 1; head[11] = 0
        o.write(head)
        o.write(byteArrayOf(0xC0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 60, 0, 4, 1, 2, 3, 4))
        assertTrue(DnsResponse.cnameTargets(o.toByteArray()).isEmpty())
    }
}
