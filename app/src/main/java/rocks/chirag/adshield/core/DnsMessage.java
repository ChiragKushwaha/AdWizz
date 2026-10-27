package rocks.chirag.adshield.core;

import java.io.ByteArrayOutputStream;
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
        int flags = Bytes.u16(b, 2);
        boolean isResponse = (flags & 0x8000) != 0;
        int opcode = (flags >> 11) & 0xF;
        int qdcount = Bytes.u16(b, 4);
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
        int qtype = Bytes.u16(b, pos);
        pos += 4; // qtype + qclass
        return new DnsMessage(Bytes.u16(b, 0), name.toString().toLowerCase(Locale.ROOT), qtype, pos);
    }

    /**
     * Builds a response to a blocked query. A/AAAA get an unroutable 0.0.0.0 / :: answer
     * (apps fail fast instead of retrying), everything else gets an empty NOERROR answer.
     */
    public static byte[] blockedResponse(byte[] query, DnsMessage q) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(q.questionEnd + 28);
        out.write(query, 0, 2);                                  // ID
        int reqFlags = Bytes.u16(query, 2);
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

    /** SERVFAIL, used when the upstream resolver can't be reached. */
    public static byte[] servFail(byte[] query, DnsMessage q) {
        return errorResponse(query, q, 2);
    }

    /** NXDOMAIN ("no such domain"). */
    public static byte[] nxDomain(byte[] query, DnsMessage q) {
        return errorResponse(query, q, 3);
    }

    private static byte[] errorResponse(byte[] query, DnsMessage q, int rcode) {
        byte[] r = new byte[q.questionEnd];
        System.arraycopy(query, 0, r, 0, q.questionEnd);
        int reqFlags = Bytes.u16(query, 2);
        Bytes.put16(r, 2, 0x8000 | 0x0080 | (reqFlags & 0x0100) | rcode);
        Bytes.put16(r, 4, 1);
        Bytes.put16(r, 6, 0);
        Bytes.put16(r, 8, 0);
        Bytes.put16(r, 10, 0);
        return r;
    }

    private static void write16(ByteArrayOutputStream o, int v) {
        o.write((v >> 8) & 0xFF);
        o.write(v & 0xFF);
    }
}
