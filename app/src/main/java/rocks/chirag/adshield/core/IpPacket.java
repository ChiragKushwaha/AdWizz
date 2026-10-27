package rocks.chirag.adshield.core;

/**
 * Minimal IPv4/IPv6 + UDP parsing/building for packets read from the TUN interface.
 * Pure Java with no Android dependencies so it can be unit-tested on the JVM.
 */
public final class IpPacket {
    public static final int PROTO_TCP = 6;
    public static final int PROTO_UDP = 17;
    public static final int PROTO_ICMP = 1;
    public static final int PROTO_ICMPV6 = 58;

    public final int version;      // 4 or 6
    public final byte[] srcAddr;   // 4 or 16 bytes
    public final byte[] dstAddr;   // 4 or 16 bytes
    public final int protocol;
    public final int srcPort;      // UDP only, -1 otherwise
    public final int dstPort;      // UDP only, -1 otherwise
    public final byte[] payload;   // UDP payload, empty for non-UDP

    private IpPacket(int version, byte[] src, byte[] dst, int protocol, int srcPort, int dstPort, byte[] payload) {
        this.version = version;
        this.srcAddr = src;
        this.dstAddr = dst;
        this.protocol = protocol;
        this.srcPort = srcPort;
        this.dstPort = dstPort;
        this.payload = payload;
    }

    /** Returns null for anything that isn't a well-formed IPv4 or IPv6 packet. */
    public static IpPacket parse(byte[] buf, int len) {
        if (len < 1) return null;
        int version = (buf[0] >> 4) & 0xF;
        if (version == 4) return parse4(buf, len);
        if (version == 6) return parse6(buf, len);
        return null;
    }

    private static IpPacket parse4(byte[] buf, int len) {
        if (len < 20) return null;
        int ihl = (buf[0] & 0xF) * 4;
        int totalLen = u16(buf, 2);
        if (ihl < 20 || totalLen > len || totalLen < ihl) return null;
        int fragField = u16(buf, 6);
        boolean fragmented = (fragField & 0x2000) != 0 || (fragField & 0x1FFF) != 0;
        int protocol = buf[9] & 0xFF;
        byte[] src = slice(buf, 12, 4);
        byte[] dst = slice(buf, 16, 4);
        if (protocol != PROTO_UDP || fragmented) return new IpPacket(4, src, dst, protocol, -1, -1, new byte[0]);
        return udp(4, src, dst, buf, ihl, totalLen);
    }

    private static IpPacket parse6(byte[] buf, int len) {
        if (len < 40) return null;
        int payloadLen = u16(buf, 4);
        int next = buf[6] & 0xFF;
        if (40 + payloadLen > len) return null;
        byte[] src = slice(buf, 8, 16);
        byte[] dst = slice(buf, 24, 16);
        // Extension headers are rare for DNS; treat anything but plain UDP as "other".
        if (next != PROTO_UDP) return new IpPacket(6, src, dst, next, -1, -1, new byte[0]);
        return udp(6, src, dst, buf, 40, 40 + payloadLen);
    }

    private static IpPacket udp(int v, byte[] src, byte[] dst, byte[] buf, int off, int end) {
        if (end < off + 8) return null;
        int sport = u16(buf, off);
        int dport = u16(buf, off + 2);
        int udpLen = u16(buf, off + 4);
        if (udpLen < 8 || off + udpLen > end) return null;
        return new IpPacket(v, src, dst, PROTO_UDP, sport, dport, slice(buf, off + 8, udpLen - 8));
    }

    /** Builds an IPv4 or IPv6 UDP packet depending on the address length. */
    public static byte[] buildUdp(byte[] src, byte[] dst, int srcPort, int dstPort, byte[] payload) {
        boolean v6 = src.length == 16;
        int hdr = v6 ? 40 : 20;
        int udpLen = 8 + payload.length;
        byte[] p = new byte[hdr + udpLen];
        writeIpHeader(p, v6, src, dst, PROTO_UDP, udpLen);
        put16(p, hdr, srcPort);
        put16(p, hdr + 2, dstPort);
        put16(p, hdr + 4, udpLen);
        System.arraycopy(payload, 0, p, hdr + 8, payload.length);
        int c = checksum(p, hdr, udpLen, pseudoSum(src, dst, PROTO_UDP, udpLen));
        if (c == 0) c = 0xFFFF;
        put16(p, hdr + 6, c);
        return p;
    }

    /**
     * ICMP / ICMPv6 "destination unreachable, administratively prohibited" for a packet we refuse
     * to carry (e.g. DNS-over-TLS/HTTPS to a hard-coded resolver). Lets the app fail fast and fall
     * back to plain DNS, which we filter, instead of hanging until a timeout.
     */
    public static byte[] buildIcmpUnreachable(byte[] original, int originalLen) {
        boolean v6 = ((original[0] >> 4) & 0xF) == 6;
        int hdr = v6 ? 40 : 20;
        byte[] src = v6 ? slice(original, 24, 16) : slice(original, 16, 4); // reply from the target
        byte[] dst = v6 ? slice(original, 8, 16) : slice(original, 12, 4);  // back to the sender
        int quoted = v6 ? Math.min(originalLen, 1280 - 48) : Math.min(originalLen, (original[0] & 0xF) * 4 + 8);
        int icmpLen = 8 + quoted;
        byte[] p = new byte[hdr + icmpLen];
        int proto = v6 ? PROTO_ICMPV6 : PROTO_ICMP;
        writeIpHeader(p, v6, src, dst, proto, icmpLen);
        p[hdr] = (byte) (v6 ? 1 : 3);       // destination unreachable
        p[hdr + 1] = (byte) (v6 ? 1 : 13);  // administratively prohibited
        System.arraycopy(original, 0, p, hdr + 8, quoted);
        long initial = v6 ? pseudoSum(src, dst, proto, icmpLen) : 0; // ICMPv6 checksum covers a pseudo-header
        put16(p, hdr + 2, checksum(p, hdr, icmpLen, initial));
        return p;
    }

    private static void writeIpHeader(byte[] p, boolean v6, byte[] src, byte[] dst, int proto, int payloadLen) {
        if (v6) {
            p[0] = 0x60;
            put16(p, 4, payloadLen);
            p[6] = (byte) proto;
            p[7] = 64; // hop limit
            System.arraycopy(src, 0, p, 8, 16);
            System.arraycopy(dst, 0, p, 24, 16);
        } else {
            p[0] = 0x45;
            put16(p, 2, 20 + payloadLen);
            put16(p, 6, 0x4000);  // don't fragment
            p[8] = 64;            // TTL
            p[9] = (byte) proto;
            System.arraycopy(src, 0, p, 12, 4);
            System.arraycopy(dst, 0, p, 16, 4);
            put16(p, 10, checksum(p, 0, 20, 0));
        }
    }

    private static long pseudoSum(byte[] src, byte[] dst, int proto, int len) {
        long s = 0;
        for (int i = 0; i < src.length; i += 2) s += u16(src, i) + u16(dst, i);
        return s + proto + len;
    }

    static int checksum(byte[] b, int off, int len, long initial) {
        long sum = initial;
        int i = off;
        int end = off + len;
        while (i + 1 < end) {
            sum += ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
            i += 2;
        }
        if (i < end) sum += (b[i] & 0xFF) << 8;
        while ((sum >> 16) != 0) sum = (sum & 0xFFFF) + (sum >> 16);
        return (int) (~sum & 0xFFFF);
    }

    private static byte[] slice(byte[] b, int off, int len) {
        byte[] r = new byte[len];
        System.arraycopy(b, off, r, 0, len);
        return r;
    }

    static int u16(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    static void put16(byte[] b, int off, int v) {
        b[off] = (byte) (v >> 8);
        b[off + 1] = (byte) v;
    }
}
