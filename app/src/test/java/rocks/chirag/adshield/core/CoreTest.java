package rocks.chirag.adshield.core;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class CoreTest {

    /** dig-style query for "ads.example.com" A with an EDNS OPT record. */
    static byte[] sampleQuery(String name, int qtype) {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        o.write(0x12); o.write(0x34);       // id
        o.write(0x01); o.write(0x20);       // RD + AD
        o.write(0); o.write(1);             // qd
        o.write(0); o.write(0);
        o.write(0); o.write(0);
        o.write(0); o.write(1);             // ar (EDNS)
        for (String label : name.split("\\.")) {
            o.write(label.length());
            o.writeBytes(label.getBytes());
        }
        o.write(0);
        o.write(qtype >> 8); o.write(qtype);
        o.write(0); o.write(1);
        // OPT RR
        o.write(0); o.write(0); o.write(41); o.write(0x10); o.write(0); o.write(0); o.write(0); o.write(0); o.write(0); o.write(0);
        return o.toByteArray();
    }

    @Test public void parsesQuery() {
        DnsMessage q = DnsMessage.parseQuery(sampleQuery("Ads.Example.COM", 1));
        assertNotNull(q);
        assertEquals("ads.example.com", q.qname);
        assertEquals(1, q.qtype);
        assertEquals(0x1234, q.id);
    }

    @Test public void blockedResponseForA() {
        byte[] query = sampleQuery("ads.example.com", 1);
        DnsMessage q = DnsMessage.parseQuery(query);
        byte[] r = DnsMessage.blockedResponse(query, q);
        assertEquals(0x12, r[0]);
        assertTrue((r[2] & 0x80) != 0);              // QR
        assertEquals(1, IpPacket.u16(r, 6));          // ANCOUNT
        assertEquals(0, IpPacket.u16(r, 10));         // ARCOUNT stripped
        assertEquals(q.questionEnd + 16, r.length);   // 12-byte RR header + 4-byte IP
    }

    @Test public void ipUdpRoundTripAndChecksums() {
        byte[] payload = sampleQuery("example.com", 28);
        byte[] src = {10, 111, (byte) 222, 2};
        byte[] dst = {10, 111, (byte) 222, 1};
        byte[] pkt = IpPacket.buildUdp(src, dst, 40000, 53, payload);
        assertEquals(0, IpPacket.checksum(pkt, 0, 20, 0));
        IpPacket p = IpPacket.parse(pkt, pkt.length);
        assertNotNull(p);
        assertEquals(53, p.dstPort);
        assertEquals(40000, p.srcPort);
        assertArrayEquals(payload, p.payload);
    }

    @Test public void ipv6UdpRoundTrip() {
        byte[] payload = sampleQuery("example.com", 1);
        byte[] src = new byte[16];
        byte[] dst = new byte[16];
        src[0] = (byte) 0xfd; src[15] = 2;
        dst[0] = (byte) 0xfd; dst[15] = 1;
        byte[] pkt = IpPacket.buildUdp(src, dst, 5353, 53, payload);
        IpPacket p = IpPacket.parse(pkt, pkt.length);
        assertNotNull(p);
        assertEquals(6, p.version);
        assertEquals(53, p.dstPort);
        assertArrayEquals(payload, p.payload);
        assertArrayEquals(dst, p.dstAddr);
    }

    @Test public void tcpIsReportedAsNonUdp() {
        byte[] pkt = IpPacket.buildUdp(new byte[]{1, 1, 1, 1}, new byte[]{10, 0, 0, 2}, 1, 2, new byte[4]);
        pkt[9] = IpPacket.PROTO_TCP;
        IpPacket p = IpPacket.parse(pkt, pkt.length);
        assertNotNull(p);
        assertEquals(IpPacket.PROTO_TCP, p.protocol);
        assertEquals(-1, p.dstPort);
    }

    @Test public void rejectsGarbage() {
        assertNull(IpPacket.parse(new byte[]{0x45, 0, 0}, 3));
        assertNull(DnsMessage.parseQuery(new byte[5]));
        byte[] resp = sampleQuery("a.com", 1);
        resp[2] |= (byte) 0x80; // a response, not a query
        assertNull(DnsMessage.parseQuery(resp));
    }

    @Test public void matcherHandlesParentsAndAllowlist() throws Exception {
        Set<String> blocked = new HashSet<>();
        DomainMatcher.parseInto(new StringReader(
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

    @Test public void findsCnameTargetsWithCompression() {
        byte[] q = sampleQuery("metrics.news.com", 1);
        DnsMessage dq = DnsMessage.parseQuery(q);
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        o.write(q, 0, dq.questionEnd);
        byte[] r = o.toByteArray();
        r[2] = (byte) 0x81; r[3] = (byte) 0x80;
        r[7] = 2; r[11] = 0;                       // ANCOUNT=2, ARCOUNT=0
        o.reset(); o.write(r, 0, r.length);
        // metrics.news.com CNAME tracker.adnet.io
        o.write(0xC0); o.write(12); o.write(0); o.write(5); o.write(0); o.write(1);
        o.write(0); o.write(0); o.write(0); o.write(60);
        byte[] target = {7,'t','r','a','c','k','e','r',5,'a','d','n','e','t',2,'i','o',0};
        o.write(0); o.write(target.length); o.write(target, 0, target.length);
        int targetOff = r.length + 12;
        // tracker.adnet.io A 1.2.3.4 (owner name is a pointer to the CNAME target)
        o.write(0xC0 | (targetOff >> 8)); o.write(targetOff & 0xFF);
        o.write(0); o.write(1); o.write(0); o.write(1);
        o.write(0); o.write(0); o.write(0); o.write(60);
        o.write(0); o.write(4); o.write(1); o.write(2); o.write(3); o.write(4);
        java.util.List<String> names = DnsMessage.cnameTargets(o.toByteArray());
        assertEquals(java.util.List.of("tracker.adnet.io"), names);
        assertTrue(DnsMessage.cnameTargets(new byte[]{1, 2, 3}).isEmpty());
    }

    @Test public void parsesWildcardRules() {
        assertEquals("ads.example.com", DomainMatcher.parseLine("*.ads.example.com"));
        assertEquals("ads.example.com", DomainMatcher.parseLine("||*.ads.example.com^"));
        assertEquals("ads.example.com", DomainMatcher.parseLine("||ads.example.com^$important"));
    }

    @Test public void nxDomainHasRcode3() {
        byte[] query = sampleQuery("use-application-dns.net", 1);
        byte[] r = DnsMessage.nxDomain(query, DnsMessage.parseQuery(query));
        assertEquals(3, r[3] & 0x0F);
        assertTrue((r[2] & 0x80) != 0);
    }
}
