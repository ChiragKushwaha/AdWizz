package rocks.chirag.adwizz.core;

/** Hand-built DNS packets shared by the core tests. */
public final class DnsFixtures {
    private DnsFixtures() {}
    public static byte[] sampleQuery(String name, int qtype) {
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
}
