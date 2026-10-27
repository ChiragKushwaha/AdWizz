package rocks.chirag.adwizz.core;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** UDP packet round trips and parent-domain matching with an allowlist. */
public class PacketMatcherTest {
    @Test public void ipUdpRoundTripAndChecksums() {
        byte[] payload = DnsFixtures.sampleQuery("example.com", 28);
        byte[] src = {10, 111, (byte) 222, 2};
        byte[] dst = {10, 111, (byte) 222, 1};
        byte[] pkt = IpBuilder.udp(src, dst, 40000, 53, payload);
        assertEquals(0, Bytes.checksum(pkt, 0, 20, 0));
        IpPacket p = IpPacket.parse(pkt, pkt.length);
        assertNotNull(p);
        assertEquals(53, p.dstPort);
        assertEquals(40000, p.srcPort);
        assertArrayEquals(payload, p.payload);
    }
    @Test public void ipv6UdpRoundTrip() {
        byte[] payload = DnsFixtures.sampleQuery("example.com", 1);
        byte[] src = new byte[16];
        byte[] dst = new byte[16];
        src[0] = (byte) 0xfd; src[15] = 2;
        dst[0] = (byte) 0xfd; dst[15] = 1;
        byte[] pkt = IpBuilder.udp(src, dst, 5353, 53, payload);
        IpPacket p = IpPacket.parse(pkt, pkt.length);
        assertNotNull(p);
        assertEquals(6, p.version);
        assertEquals(53, p.dstPort);
        assertArrayEquals(payload, p.payload);
        assertArrayEquals(dst, p.dstAddr);
    }
    @Test public void tcpIsReportedAsNonUdp() {
        byte[] pkt = IpBuilder.udp(new byte[]{1, 1, 1, 1}, new byte[]{10, 0, 0, 2}, 1, 2, new byte[4]);
        pkt[9] = IpPacket.PROTO_TCP;
        IpPacket p = IpPacket.parse(pkt, pkt.length);
        assertNotNull(p);
        assertEquals(IpPacket.PROTO_TCP, p.protocol);
        assertEquals(-1, p.dstPort);
    }
    @Test public void matcherHandlesParentsAndAllowlist() throws Exception {
        Set<String> blocked = new HashSet<>();
        ListParser.parseInto(new StringReader(
            "# comment\n0.0.0.0 doubleclick.net\n127.0.0.1 localhost\n||ads.tiktok.com^\nplain-tracker.io\n" +
            "||bad.com/path^\n0.0.0.0 0.0.0.0\n10.0.0.1 notblocked.com\n"), blocked);
        assertEquals(3, blocked.size());
        Set<String> allowed = new HashSet<>();
        allowed.add("ok.doubleclick.net");
        DomainMatcher m = new DomainMatcher(blocked, allowed);
        assertTrue(m.isBlocked("doubleclick.net"));
        assertTrue(m.isBlocked("googleads.g.doubleclick.net"));
        assertFalse(m.isBlocked("ok.doubleclick.net"));
        assertFalse(m.isBlocked("sub.ok.doubleclick.net"));
        assertFalse(m.isBlocked("notdoubleclick.net"));
        assertTrue(m.isBlocked("ads.tiktok.com"));
        assertFalse(m.isBlocked("tiktok.com"));
        assertFalse(m.isBlocked("notblocked.com"));
    }
}
