package rocks.chirag.adshield.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.StringReader

/** IPv4/IPv6 parsing, ICMP refusals and checksums. */
class IpPacketTest {

    private fun query(name: String, qtype: Int) = DnsFixtures.sampleQuery(name, qtype)


    @Test fun icmpUnreachableForIpv4SwapsAddressesAndHasValidChecksums() {
        val udp = IpBuilder.udp(byteArrayOf(10, 0, 0, 2), byteArrayOf(8, 8, 8, 8), 5555, 853, ByteArray(10))
        val icmp = IpBuilder.icmpUnreachable(udp, udp.size)
        val p = IpPacket.parse(icmp, icmp.size)!!
        assertEquals(IpPacket.PROTO_ICMP, p.protocol)
        assertArrayEquals(byteArrayOf(8, 8, 8, 8), p.srcAddr)
        assertArrayEquals(byteArrayOf(10, 0, 0, 2), p.dstAddr)
        assertEquals(3, icmp[20].toInt())   // destination unreachable
        assertEquals(13, icmp[21].toInt())  // administratively prohibited
        assertEquals(0, Bytes.checksum(icmp, 0, 20, 0))
        assertEquals(0, Bytes.checksum(icmp, 20, icmp.size - 20, 0))
    }

    @Test fun icmpUnreachableForIpv6() {
        val src = ByteArray(16).also { it[0] = 0xfd.toByte(); it[15] = 2 }
        val dst = ByteArray(16).also { it[0] = 0x20; it[15] = 1 }
        val udp = IpBuilder.udp(src, dst, 5555, 443, ByteArray(10))
        val icmp = IpBuilder.icmpUnreachable(udp, udp.size)
        val p = IpPacket.parse(icmp, icmp.size)!!
        assertEquals(6, p.version)
        assertEquals(IpPacket.PROTO_ICMPV6, p.protocol)
        assertArrayEquals(dst, p.srcAddr)
        assertArrayEquals(src, p.dstAddr)
        assertEquals(1, icmp[40].toInt())
    }

    @Test fun fragmentedIpv4IsNotTreatedAsUdp() {
        val pkt = IpBuilder.udp(byteArrayOf(10, 0, 0, 2), byteArrayOf(10, 0, 0, 1), 1, 53, ByteArray(4))
        pkt[6] = 0x20 // more-fragments
        val p = IpPacket.parse(pkt, pkt.size)!!
        assertEquals(-1, p.dstPort)
    }

    @Test fun rejectsBadLengthsAndVersions() {
        val pkt = IpBuilder.udp(byteArrayOf(10, 0, 0, 2), byteArrayOf(10, 0, 0, 1), 1, 53, ByteArray(4))
        assertNull(IpPacket.parse(pkt, pkt.size - 1)) // total length > bytes read
        val v5 = pkt.copyOf(); v5[0] = 0x55
        assertNull(IpPacket.parse(v5, v5.size))
        assertNull(IpPacket.parse(ByteArray(0), 0))
        assertNull(IpPacket.parse(ByteArray(30).also { it[0] = 0x60 }, 30))
    }

    @Test fun udpChecksumIsValidForIpv4() {
        val pkt = IpBuilder.udp(byteArrayOf(10, 0, 0, 1), byteArrayOf(10, 0, 0, 2), 53, 4000, ByteArray(7) { it.toByte() })
        // Pseudo-header sum + UDP segment must fold to zero.
        var sum = 0L
        for (i in 12 until 20 step 2) sum += Bytes.u16(pkt, i)
        sum += IpPacket.PROTO_UDP + (pkt.size - 20)
        assertEquals(0, Bytes.checksum(pkt, 20, pkt.size - 20, sum))
    }
}
