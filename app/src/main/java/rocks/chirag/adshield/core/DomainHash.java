package rocks.chirag.adshield.core;

/**
 * FNV-1a over the domain's bytes, then a final mix so [DomainSet]'s linear probing doesn't cluster.
 * The two entry points must agree: one hashes a (sub)string, the other a stored [len][bytes] entry.
 */
final class DomainHash {
    private DomainHash() {}

    static int of(CharSequence s, int start, int len) {
        int h = 0x811C9DC5;
        for (int i = 0; i < len; i++) {
            h ^= s.charAt(start + i) & 0xFF;
            h *= 0x01000193;
        }
        return mix(h);
    }

    static int stored(byte[] data, int off) {
        int len = data[off] & 0xFF;
        int h = 0x811C9DC5;
        for (int i = 0; i < len; i++) {
            h ^= data[off + 1 + i] & 0xFF;
            h *= 0x01000193;
        }
        return mix(h);
    }

    private static int mix(int h) {
        h ^= h >>> 16;
        h *= 0x85EBCA6B;
        h ^= h >>> 13;
        return h;
    }
}
