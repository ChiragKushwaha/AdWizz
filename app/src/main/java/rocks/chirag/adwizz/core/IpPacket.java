package rocks.chirag.adwizz.core;

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
        int totalLen = Bytes.u16(buf, 2);
        if (ihl < 20 || totalLen > len || totalLen < ihl) return null;
        int fragField = Bytes.u16(buf, 6);
        boolean fragmented = (fragField & 0x2000) != 0 || (fragField & 0x1FFF) != 0;
        int protocol = buf[9] & 0xFF;
        byte[] src = Bytes.slice(buf, 12, 4);
        byte[] dst = Bytes.slice(buf, 16, 4);
        if (protocol != PROTO_UDP || fragmented) return new IpPacket(4, src, dst, protocol, -1, -1, new byte[0]);
        return udp(4, src, dst, buf, ihl, totalLen);
    }

    private static IpPacket parse6(byte[] buf, int len) {
        if (len < 40) return null;
        int payloadLen = Bytes.u16(buf, 4);
        int next = buf[6] & 0xFF;
        if (40 + payloadLen > len) return null;
        byte[] src = Bytes.slice(buf, 8, 16);
        byte[] dst = Bytes.slice(buf, 24, 16);
        // Extension headers are rare for DNS; treat anything but plain UDP as "other".
        if (next != PROTO_UDP) return new IpPacket(6, src, dst, next, -1, -1, new byte[0]);
        return udp(6, src, dst, buf, 40, 40 + payloadLen);
    }

    private static IpPacket udp(int v, byte[] src, byte[] dst, byte[] buf, int off, int end) {
        if (end < off + 8) return null;
        int sport = Bytes.u16(buf, off);
        int dport = Bytes.u16(buf, off + 2);
        int udpLen = Bytes.u16(buf, off + 4);
        if (udpLen < 8 || off + udpLen > end) return null;
        return new IpPacket(v, src, dst, PROTO_UDP, sport, dport, Bytes.slice(buf, off + 8, udpLen - 8));
    }
}
