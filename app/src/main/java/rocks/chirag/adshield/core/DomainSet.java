package rocks.chirag.adshield.core;

import java.util.Arrays;

/**
 * Memory-compact set of lower-case ASCII domain names.
 *
 * A HashSet&lt;String&gt; of ~800k domains costs ~100 MB (each String plus its hash node is ~120
 * bytes). Here every domain is stored once as [length][bytes] in a single byte array, and an
 * open-addressing table of int offsets points into it: ~25 bytes per domain, no per-entry objects,
 * nothing for the garbage collector to trace. Lookups don't allocate, so the parent-domain walk
 * for every DNS query is cheap.
 *
 * Not thread-safe while being built; safe for concurrent reads once built (swap whole instances).
 */
public final class DomainSet {
    private static final int MAX_LEN = 255;

    private byte[] data;
    private int dataLen = 1; // offset 0 is reserved so table value 0 can mean "empty"
    private int[] table;
    private int count;

    public DomainSet() { this(1024); }

    /** @param expected rough number of domains, to avoid rehashing while loading big lists */
    public DomainSet(int expected) {
        int cap = Integer.highestOneBit(Math.max(16, (int) (expected / 0.7f)) * 2 - 1);
        table = new int[cap];
        data = new byte[Math.max(64, expected * 24)];
    }

    public int size() { return count; }

    /** Adds a domain (must already be lower-case ASCII). Returns false if it was already present. */
    public boolean add(String d) {
        int len = d.length();
        if (len == 0 || len > MAX_LEN) return false;
        if ((count + 1) > table.length * 0.7f) rehash(table.length * 2);
        int h = DomainHash.of(d, 0, len);
        int mask = table.length - 1;
        int slot = h & mask;
        while (true) {
            int off = table[slot];
            if (off == 0) break;
            if (equalsAt(off, d, 0, len)) return false;
            slot = (slot + 1) & mask;
        }
        ensureData(len + 1);
        int off = dataLen;
        data[dataLen++] = (byte) len;
        for (int i = 0; i < len; i++) data[dataLen++] = (byte) d.charAt(i);
        table[slot] = off;
        count++;
        return true;
    }

    public boolean contains(CharSequence s) { return contains(s, 0); }

    /** True if s.subSequence(start, s.length()) is in the set — without creating that substring. */
    public boolean contains(CharSequence s, int start) {
        int len = s.length() - start;
        if (len <= 0 || len > MAX_LEN || count == 0) return false;
        int mask = table.length - 1;
        int slot = DomainHash.of(s, start, len) & mask;
        while (true) {
            int off = table[slot];
            if (off == 0) return false;
            if (equalsAt(off, s, start, len)) return true;
            slot = (slot + 1) & mask;
        }
    }

    /** Frees the unused tail of the byte buffer once loading is done. */
    public DomainSet trim() {
        if (data.length > dataLen) data = Arrays.copyOf(data, dataLen);
        return this;
    }

    /** Approximate heap used by this set, in bytes (for diagnostics and tests). */
    public long memoryBytes() { return (long) data.length + 4L * table.length; }

    private boolean equalsAt(int off, CharSequence s, int start, int len) {
        if ((data[off] & 0xFF) != len) return false;
        int p = off + 1;
        for (int i = 0; i < len; i++) {
            char c = s.charAt(start + i);
            if (c > 0x7F || data[p + i] != (byte) c) return false; // stored names are ASCII
        }
        return true;
    }

    private void rehash(int newCap) {
        int[] old = table;
        table = new int[newCap];
        int mask = newCap - 1;
        for (int off : old) {
            if (off == 0) continue;
            int slot = DomainHash.stored(data, off) & mask;
            while (table[slot] != 0) slot = (slot + 1) & mask;
            table[slot] = off;
        }
    }

    private void ensureData(int extra) {
        if (dataLen + extra <= data.length) return;
        // Grow by 1.5x: less transient memory than doubling while big lists load.
        data = Arrays.copyOf(data, Math.max(dataLen + extra, data.length + (data.length >> 1)));
    }
}
