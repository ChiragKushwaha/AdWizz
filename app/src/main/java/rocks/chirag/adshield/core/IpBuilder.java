package rocks.chirag.adshield.core;

/** Builds the packets the VPN writes back into the tunnel: DNS answers and "refused" ICMP. */
public final class IpBuilder {
    private IpBuilder() {}

    /** Builds an IPv4 or IPv6 UDP packet depending on the address length. */
    public static byte[] udp(byte[] src, byte[] dst, int srcPort, int dstPort, byte[] payload) {
        boolean v6 = src.length == 16;
        int hdr = v6 ? 40 : 20;
        int udpLen = 8 + payload.length;
        byte[] p = new byte[hdr + udpLen];
        writeIpHeader(p, v6, src, dst, IpPacket.PROTO_UDP, udpLen);
        Bytes.put16(p, hdr, srcPort);
        Bytes.put16(p, hdr + 2, dstPort);
        Bytes.put16(p, hdr + 4, udpLen);
        System.arraycopy(payload, 0, p, hdr + 8, payload.length);
        int c = Bytes.checksum(p, hdr, udpLen, pseudoSum(src, dst, IpPacket.PROTO_UDP, udpLen));
        if (c == 0) c = 0xFFFF;
        Bytes.put16(p, hdr + 6, c);
        return p;
    }

    /**
     * ICMP / ICMPv6 "destination unreachable, administratively prohibited" for a packet we refuse
     * to carry (e.g. DNS-over-TLS/HTTPS to a hard-coded resolver). Lets the app fail fast and fall
     * back to plain DNS, which we filter, instead of hanging until a timeout.
     */
    public static byte[] icmpUnreachable(byte[] original, int originalLen) {
        boolean v6 = ((original[0] >> 4) & 0xF) == 6;
        int hdr = v6 ? 40 : 20;
        byte[] src = v6 ? Bytes.slice(original, 24, 16) : Bytes.slice(original, 16, 4); // reply from the target
        byte[] dst = v6 ? Bytes.slice(original, 8, 16) : Bytes.slice(original, 12, 4);  // back to the sender
        int quoted = v6 ? Math.min(originalLen, 1280 - 48) : Math.min(originalLen, (original[0] & 0xF) * 4 + 8);
        int icmpLen = 8 + quoted;
        byte[] p = new byte[hdr + icmpLen];
        int proto = v6 ? IpPacket.PROTO_ICMPV6 : IpPacket.PROTO_ICMP;
        writeIpHeader(p, v6, src, dst, proto, icmpLen);
        p[hdr] = (byte) (v6 ? 1 : 3);       // destination unreachable
        p[hdr + 1] = (byte) (v6 ? 1 : 13);  // administratively prohibited
        System.arraycopy(original, 0, p, hdr + 8, quoted);
        long initial = v6 ? pseudoSum(src, dst, proto, icmpLen) : 0; // ICMPv6 checksum covers a pseudo-header
        Bytes.put16(p, hdr + 2, Bytes.checksum(p, hdr, icmpLen, initial));
        return p;
    }

    private static void writeIpHeader(byte[] p, boolean v6, byte[] src, byte[] dst, int proto, int payloadLen) {
        if (v6) {
            p[0] = 0x60;
            Bytes.put16(p, 4, payloadLen);
            p[6] = (byte) proto;
            p[7] = 64; // hop limit
            System.arraycopy(src, 0, p, 8, 16);
            System.arraycopy(dst, 0, p, 24, 16);
        } else {
            p[0] = 0x45;
            Bytes.put16(p, 2, 20 + payloadLen);
            Bytes.put16(p, 6, 0x4000);  // don't fragment
            p[8] = 64;            // TTL
            p[9] = (byte) proto;
            System.arraycopy(src, 0, p, 12, 4);
            System.arraycopy(dst, 0, p, 16, 4);
            Bytes.put16(p, 10, Bytes.checksum(p, 0, 20, 0));
        }
    }

    private static long pseudoSum(byte[] src, byte[] dst, int proto, int len) {
        long s = 0;
        for (int i = 0; i < src.length; i += 2) s += Bytes.u16(src, i) + Bytes.u16(dst, i);
        return s + proto + len;
    }
}
