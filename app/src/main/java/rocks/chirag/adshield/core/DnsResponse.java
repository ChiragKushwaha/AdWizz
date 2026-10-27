package rocks.chirag.adshield.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reading upstream DNS answers: is it really ours, and which names does it point to. */
public final class DnsResponse {
    private DnsResponse() {}

    /** True if [response] is a DNS response carrying the same ID as [query]. */
    public static boolean isResponseTo(byte[] query, byte[] response) {
        if (query == null || response == null || query.length < 12 || response.length < 12) return false;
        boolean isResponse = (response[2] & 0x80) != 0;
        return isResponse && Bytes.u16(query, 0) == Bytes.u16(response, 0);
    }

    /**
     * CNAME targets in a response's answer section. Used to catch "CNAME cloaking", where an
     * innocent-looking subdomain is just an alias for an ad/tracker domain.
     * Returns an empty list for malformed input.
     */
    public static List<String> cnameTargets(byte[] b) {
        List<String> out = new ArrayList<>();
        if (b == null || b.length < 12) return out;
        int qd = Bytes.u16(b, 4);
        int an = Bytes.u16(b, 6);
        int[] pos = {12};
        for (int i = 0; i < qd; i++) {
            if (readName(b, pos) == null) return out;
            pos[0] += 4;
        }
        for (int i = 0; i < an; i++) {
            if (readName(b, pos) == null) return out;
            if (pos[0] + 10 > b.length) return out;
            int type = Bytes.u16(b, pos[0]);
            int rdlen = Bytes.u16(b, pos[0] + 8);
            int rdata = pos[0] + 10;
            if (rdata + rdlen > b.length) return out;
            if (type == DnsMessage.TYPE_CNAME) {
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
}
