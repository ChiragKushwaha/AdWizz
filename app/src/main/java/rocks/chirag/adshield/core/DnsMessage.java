package rocks.chirag.adshield.core;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Just enough DNS to read the first question and synthesise a "blocked" answer. */
public final class DnsMessage {
    public static final int TYPE_A = 1;
    public static final int TYPE_CNAME = 5;
    public static final int TYPE_AAAA = 28;
    public static final int TYPE_HTTPS = 65;

    public final int id;
    public final String qname;   // lower-case, no trailing dot
    public final int qtype;
    /** Offset just past the first question (where answers would start). */
    final int questionEnd;

    private DnsMessage(int id, String qname, int qtype, int questionEnd) {
        this.id = id;
        this.qname = qname;
        this.qtype = qtype;
        this.questionEnd = questionEnd;
    }

    /** Returns null if this isn't a standard query with at least one question. */
    public static DnsMessage parseQuery(byte[] b) {
        if (b.length < 12) return null;
        int flags = IpPacket.u16(b, 2);
        boolean isResponse = (flags & 0x8000) != 0;
        int opcode = (flags >> 11) & 0xF;
        int qdcount = IpPacket.u16(b, 4);
        if (isResponse || opcode != 0 || qdcount < 1) return null;

        StringBuilder name = new StringBuilder();
        int pos = 12;
        while (true) {
            if (pos >= b.length) return null;
            int len = b[pos] & 0xFF;
            if (len == 0) { pos++; break; }
            if ((len & 0xC0) != 0) return null; // compression isn't valid in a query's first name
            if (len > 63 || pos + 1 + len > b.length) return null;
            if (name.length() > 0) name.append('.');
            for (int i = 0; i < len; i++) name.append((char) (b[pos + 1 + i] & 0xFF));
            pos += 1 + len;
            if (name.length() > 253) return null;
        }
        if (pos + 4 > b.length) return null;
        int qtype = IpPacket.u16(b, pos);
        pos += 4; // qtype + qclass
        return new DnsMessage(IpPacket.u16(b, 0), name.toString().toLowerCase(Locale.ROOT), qtype, pos);
    }

    /**
     * Builds a response to a blocked query. A/AAAA get an unroutable 0.0.0.0 / :: answer
     * (apps fail fast instead of retrying), everything else gets an empty NOERROR answer.
     */
    public static byte[] blockedResponse(byte[] query, DnsMessage q) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(q.questionEnd + 28);
        out.write(query, 0, 2);                                  // ID
        int reqFlags = IpPacket.u16(query, 2);
        int flags = 0x8000 | 0x0080 | (reqFlags & 0x0100);       // QR, RA, copy RD
        out.write(flags >> 8);
        out.write(flags & 0xFF);
        boolean answer = q.qtype == TYPE_A || q.qtype == TYPE_AAAA;
        write16(out, 1);                                         // QDCOUNT
        write16(out, answer ? 1 : 0);                            // ANCOUNT
        write16(out, 0);                                         // NSCOUNT
        write16(out, 0);                                         // ARCOUNT (drop EDNS)
        out.write(query, 12, q.questionEnd - 12);                // question
        if (answer) {
            write16(out, 0xC00C);                                // pointer to qname
            write16(out, q.qtype);
            write16(out, 1);                                     // IN
            out.write(0); out.write(0); out.write(0); out.write(60); // TTL 60s
            int rdlen = q.qtype == TYPE_A ? 4 : 16;
            write16(out, rdlen);
            for (int i = 0; i < rdlen; i++) out.write(0);
        }
        return out.toByteArray();
    }

    /** True if [response] is a DNS response carrying the same ID as [query]. */
    public static boolean isResponseTo(byte[] query, byte[] response) {
        if (query == null || response == null || query.length < 12 || response.length < 12) return false;
        boolean isResponse = (response[2] & 0x80) != 0;
        return isResponse && IpPacket.u16(query, 0) == IpPacket.u16(response, 0);
    }

    /** SERVFAIL, used when the upstream resolver can't be reached. */
    public static byte[] servFail(byte[] query, DnsMessage q) {
        return errorResponse(query, q, 2);
    }

    /** NXDOMAIN ("no such domain"). */
    public static byte[] nxDomain(byte[] query, DnsMessage q) {
        return errorResponse(query, q, 3);
    }

    /**
     * CNAME targets in a response's answer section. Used to catch "CNAME cloaking", where an
     * innocent-looking subdomain is just an alias for an ad/tracker domain.
     * Returns an empty list for malformed input.
     */
    public static List<String> cnameTargets(byte[] b) {
        List<String> out = new ArrayList<>();
        if (b == null || b.length < 12) return out;
        int qd = IpPacket.u16(b, 4);
        int an = IpPacket.u16(b, 6);
        int[] pos = {12};
        for (int i = 0; i < qd; i++) {
            if (readName(b, pos) == null) return out;
            pos[0] += 4;
        }
        for (int i = 0; i < an; i++) {
            if (readName(b, pos) == null) return out;
            if (pos[0] + 10 > b.length) return out;
            int type = IpPacket.u16(b, pos[0]);
            int rdlen = IpPacket.u16(b, pos[0] + 8);
            int rdata = pos[0] + 10;
            if (rdata + rdlen > b.length) return out;
            if (type == TYPE_CNAME) {
                String target = readName(b, new int[]{rdata});
                if (target != null) out.add(target);
            }
            pos[0] = rdata + rdlen;
        }
        return out;
    }

    /** Reads a possibly-compressed name at pos[0] and advances pos[0] past it. Null if malformed. */
    static String readName(byte[] b, int[] pos) {
        StringBuilder sb = new StringBuilder();
        int p = pos[0];
        int after = -1;
        int jumps = 0;
        while (true) {
            if (p >= b.length) return null;
            int len = b[p] & 0xFF;
            if ((len & 0xC0) == 0xC0) {
                if (p + 1 >= b.length || ++jumps > 16) return null;
                if (after < 0) after = p + 2;
                p = ((len & 0x3F) << 8) | (b[p + 1] & 0xFF);
                continue;
            }
            if ((len & 0xC0) != 0) return null;
            if (len == 0) { p++; break; }
            if (p + 1 + len > b.length) return null;
            if (sb.length() > 0) sb.append('.');
            for (int i = 0; i < len; i++) sb.append((char) (b[p + 1 + i] & 0xFF));
            p += 1 + len;
            if (sb.length() > 253) return null;
        }
        pos[0] = after < 0 ? p : after;
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    private static byte[] errorResponse(byte[] query, DnsMessage q, int rcode) {
        byte[] r = new byte[q.questionEnd];
        System.arraycopy(query, 0, r, 0, q.questionEnd);
        int reqFlags = IpPacket.u16(query, 2);
        IpPacket.put16(r, 2, 0x8000 | 0x0080 | (reqFlags & 0x0100) | rcode);
        IpPacket.put16(r, 4, 1);
        IpPacket.put16(r, 6, 0);
        IpPacket.put16(r, 8, 0);
        IpPacket.put16(r, 10, 0);
        return r;
    }

    private static void write16(ByteArrayOutputStream o, int v) {
        o.write((v >> 8) & 0xFF);
        o.write(v & 0xFF);
    }
}
