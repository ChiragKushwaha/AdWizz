package rocks.chirag.adshield.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Holds the blocklist and allowlist. A domain is blocked if it, or any parent domain,
 * is on the blocklist — unless it or a parent is on the allowlist (allowlist wins).
 * Thread-safe for concurrent reads once built; swap whole instances to update.
 */
public final class DomainMatcher {
    private final DomainSet blocked;
    private final DomainSet allowed;
    private boolean blockGenerated = false;

    /** Also block machine-generated ad domains not on any list yet. See {@link GeneratedDomain}. */
    public DomainMatcher withGeneratedDomainBlocking(boolean on) {
        blockGenerated = on;
        return this;
    }

    public DomainMatcher(DomainSet blocked, DomainSet allowed) {
        this.blocked = blocked;
        this.allowed = allowed;
    }

    /** Convenience for small sets (tests, user lists). */
    public DomainMatcher(Set<String> blocked, Set<String> allowed) {
        this(toSet(blocked), toSet(allowed));
    }

    private static DomainSet toSet(Collection<String> domains) {
        DomainSet s = new DomainSet(domains.size());
        for (String d : domains) s.add(d);
        return s;
    }

    public int size() { return blocked.size(); }

    public boolean isBlocked(String domain) {
        if (domain == null || domain.isEmpty()) return false;
        if (matches(allowed, domain)) return false;
        return matches(blocked, domain) || (blockGenerated && GeneratedDomain.looksGenerated(domain));
    }

    /** True if the domain or a parent is on the allowlist. */
    public boolean isAllowed(String domain) {
        return domain != null && !domain.isEmpty() && matches(allowed, domain);
    }

    /** Checks the domain and each parent ("a.b.c", "b.c", "c") without creating substrings. */
    private static boolean matches(DomainSet set, String domain) {
        if (set.size() == 0) return false;
        int start = 0;
        while (true) {
            if (set.contains(domain, start)) return true;
            int dot = domain.indexOf('.', start);
            if (dot < 0) return false;
            start = dot + 1;
        }
    }

    /**
     * Parses hosts-file style ("0.0.0.0 ads.example.com"), plain domain lists ("ads.example.com"),
     * and simple Adblock-style rules ("||ads.example.com^"). Comments start with # or !.
     */
    public static void parseInto(Reader reader, Set<String> into) throws IOException {
        BufferedReader br = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader);
        String line;
        while ((line = br.readLine()) != null) {
            String d = parseLine(line);
            if (d != null) into.add(d);
        }
    }

    public static void parseInto(Reader reader, DomainSet into) throws IOException {
        BufferedReader br = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader, 1 << 16);
        String line;
        while ((line = br.readLine()) != null) {
            String d = parseLine(line);
            if (d != null) into.add(d);
        }
    }

    static String parseLine(String raw) {
        String line = raw.trim();
        if (line.isEmpty() || line.charAt(0) == '#' || line.charAt(0) == '!') return null;
        int hash = line.indexOf('#');
        if (hash >= 0) line = line.substring(0, hash).trim();

        if (line.startsWith("||")) {
            int end = line.indexOf('^');
            if (end < 0) end = line.length();
            line = line.substring(2, end);
            if (line.startsWith("*.")) line = line.substring(2);
            if (line.contains("/") || line.contains("*") || line.contains("$")) return null;
        } else {
            // "ip domain" (hosts file) or just "domain". Hand-rolled instead of split("\\s+"),
            // which compiles a regex for every one of ~1M lines.
            int ws = 0;
            int n = line.length();
            while (ws < n && !Character.isWhitespace(line.charAt(ws))) ws++;
            if (ws < n) {
                String ip = line.substring(0, ws);
                if (!(ip.equals("0.0.0.0") || ip.equals("127.0.0.1") || ip.equals("::") || ip.equals("::1"))) return null;
                int s = ws;
                while (s < n && Character.isWhitespace(line.charAt(s))) s++;
                int e = s;
                while (e < n && !Character.isWhitespace(line.charAt(e))) e++;
                line = line.substring(s, e);
            }
            // Wildcard lists ("*.ads.example.com"): parent matching already covers subdomains.
            if (line.startsWith("*.")) line = line.substring(2);
        }
        line = line.toLowerCase(Locale.ROOT);
        if (line.endsWith(".")) line = line.substring(0, line.length() - 1);
        if (!isValidDomain(line)) return null;
        switch (line) {
            case "localhost": case "localhost.localdomain": case "local": case "broadcasthost":
            case "ip6-localhost": case "ip6-loopback": case "0.0.0.0":
                return null;
            default:
                return line;
        }
    }

    static boolean isValidDomain(String d) {
        if (d.isEmpty() || d.length() > 253 || d.indexOf('.') < 0) return false;
        for (int i = 0; i < d.length(); i++) {
            char c = d.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_';
            if (!ok) return false;
        }
        return true;
    }

    public static Set<String> normalise(Collection<String> domains) {
        Set<String> out = new HashSet<>();
        for (String s : domains) {
            String d = parseLine(s);
            if (d != null) out.add(d);
        }
        return out;
    }
}
