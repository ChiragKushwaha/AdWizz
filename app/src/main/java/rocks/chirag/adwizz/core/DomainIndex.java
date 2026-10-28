package rocks.chirag.adwizz.core;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.LongBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

/**
 * On-device blocklist database: a file of sorted 64-bit domain fingerprints, memory-mapped and
 * binary-searched (~22 reads for 3 million domains, a few microseconds per lookup).
 *
 * The data lives in the file, not the app's heap: Android pages in only the parts lookups touch
 * and can drop them again under memory pressure, so millions of domains cost almost no RAM.
 * It's rebuilt only when the lists change, so turning protection on just opens the file.
 *
 * A 64-bit fingerprint means two different domains could collide; with 3 million entries the
 * chance that any given lookup hits a false match is about 1 in 6 trillion.
 *
 * File: "ADWZIDX1", entry count (long), then that many sorted fingerprints (big-endian longs).
 */
public final class DomainIndex implements DomainLookup {
    private static final long MAGIC = 0x4144575A49445831L; // "ADWZIDX1"
    private static final int HEADER = 16;

    private final LongBuffer entries;
    private final int count;

    private DomainIndex(LongBuffer entries, int count) {
        this.entries = entries;
        this.count = count;
    }

    /** Maps [file] read-only. Throws if it isn't a complete index. */
    public static DomainIndex open(File file) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r"); FileChannel ch = raf.getChannel()) {
            long size = ch.size();
            if (size < HEADER || raf.readLong() != MAGIC) throw new IOException("Not a domain index: " + file);
            long n = raf.readLong();
            if (n < 0 || n > Integer.MAX_VALUE || HEADER + n * 8 != size) throw new IOException("Truncated domain index: " + file);
            // The mapping stays valid after the channel closes.
            LongBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, HEADER, n * 8).asLongBuffer();
            return new DomainIndex(buf, (int) n);
        }
    }

    @Override public int size() { return count; }

    @Override public boolean contains(CharSequence s, int start) {
        int len = s.length() - start;
        if (len <= 0 || len > 255 || count == 0) return false;
        long key = fingerprint(s, start, len);
        int lo = 0, hi = count - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            long v = entries.get(mid);
            if (v < key) lo = mid + 1; else if (v > key) hi = mid - 1; else return true;
        }
        return false;
    }

    /** FNV-1a 64 over the domain, then SplitMix64's finaliser to spread similar names apart. */
    static long fingerprint(CharSequence s, int start, int len) {
        long h = 0xCBF29CE484222325L;
        for (int i = 0; i < len; i++) {
            h ^= s.charAt(start + i) & 0xFF;
            h *= 0x100000001B3L;
        }
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /** Collects domains, then writes the sorted, de-duplicated index. Not thread-safe. */
    public static final class Builder {
        private long[] keys;
        private int n;

        public Builder(int expected) { keys = new long[Math.max(1024, expected)]; }

        /** Adds a lower-case domain (as produced by {@link ListParser}). */
        public void add(String d) {
            if (d.isEmpty() || d.length() > 255) return;
            if (n == keys.length) keys = Arrays.copyOf(keys, keys.length + (keys.length >> 1));
            keys[n++] = fingerprint(d, 0, d.length());
        }

        /** Writes to a temp file then renames, so a crash never leaves a half-written index. Returns the entry count. */
        public int writeTo(File file) throws IOException {
            Arrays.sort(keys, 0, n);
            int unique = 0;
            for (int i = 0; i < n; i++) if (unique == 0 || keys[i] != keys[unique - 1]) keys[unique++] = keys[i];
            File tmp = new File(file.getPath() + ".tmp");
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp), 1 << 16))) {
                out.writeLong(MAGIC);
                out.writeLong(unique);
                for (int i = 0; i < unique; i++) out.writeLong(keys[i]);
            }
            if (!tmp.renameTo(file)) throw new IOException("Couldn't save " + file);
            keys = new long[0];
            return unique;
        }
    }
}
