package rocks.chirag.adshield.core;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/** Core DNS behaviour every query goes through: parse, block, CNAME cloaking, garbage. */
public class CoreTest {
    @Test public void parsesQuery() {
        DnsMessage q = DnsMessage.parseQuery(DnsFixtures.sampleQuery("Ads.Example.COM", 1));
        assertNotNull(q);
        assertEquals("ads.example.com", q.qname);
        assertEquals(1, q.qtype);
        assertEquals(0x1234, q.id);
    }
    @Test public void blockedResponseForA() {
        byte[] query = DnsFixtures.sampleQuery("ads.example.com", 1);
        DnsMessage q = DnsMessage.parseQuery(query);
        byte[] r = DnsMessage.blockedResponse(query, q);
        assertEquals(0x12, r[0]);
        assertTrue((r[2] & 0x80) != 0);              // QR
        assertEquals(1, Bytes.u16(r, 6));          // ANCOUNT
        assertEquals(0, Bytes.u16(r, 10));         // ARCOUNT stripped
        assertEquals(q.questionEnd + 16, r.length);   // 12-byte RR header + 4-byte IP
    }
    @Test public void rejectsGarbage() {
        assertNull(IpPacket.parse(new byte[]{0x45, 0, 0}, 3));
        assertNull(DnsMessage.parseQuery(new byte[5]));
        byte[] resp = DnsFixtures.sampleQuery("a.com", 1);
        resp[2] |= (byte) 0x80; // a response, not a query
        assertNull(DnsMessage.parseQuery(resp));
    }
    @Test public void findsCnameTargetsWithCompression() {
        byte[] q = DnsFixtures.sampleQuery("metrics.news.com", 1);
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
        java.util.List<String> names = DnsResponse.cnameTargets(o.toByteArray());
        assertEquals(java.util.List.of("tracker.adnet.io"), names);
        assertTrue(DnsResponse.cnameTargets(new byte[]{1, 2, 3}).isEmpty());
    }
    @Test public void parsesWildcardRules() {
        assertEquals("ads.example.com", ListParser.parseLine("*.ads.example.com"));
        assertEquals("ads.example.com", ListParser.parseLine("||*.ads.example.com^"));
        assertEquals("ads.example.com", ListParser.parseLine("||ads.example.com^$important"));
    }
    @Test public void nxDomainHasRcode3() {
        byte[] query = DnsFixtures.sampleQuery("use-application-dns.net", 1);
        byte[] r = DnsMessage.nxDomain(query, DnsMessage.parseQuery(query));
        assertEquals(3, r[3] & 0x0F);
        assertTrue((r[2] & 0x80) != 0);
    }
}
