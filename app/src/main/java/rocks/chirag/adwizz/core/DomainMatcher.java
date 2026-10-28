package rocks.chirag.adwizz.core;

import java.util.Collection;
import java.util.Set;

/**
 * Holds the blocklist and allowlist. A domain is blocked if it, or any parent domain,
 * is on the blocklist — unless it or a parent is on the allowlist (allowlist wins).
 * Thread-safe for concurrent reads once built; swap whole instances to update.
 */
public final class DomainMatcher {
    private final DomainLookup[] blocked;
    private final DomainLookup allowed;
    private boolean blockGenerated = false;

    /** Also block machine-generated ad domains not on any list yet. See {@link GeneratedDomain}. */
    public DomainMatcher withGeneratedDomainBlocking(boolean on) {
        blockGenerated = on;
        return this;
    }

    public DomainMatcher(DomainSet blocked, DomainSet allowed) {
        this(new DomainLookup[] { blocked }, allowed);
    }

    /** Blocked if any of [blocked] lists the domain or a parent: e.g. the on-disk index plus the user's own list. */
    public DomainMatcher(DomainLookup[] blocked, DomainLookup allowed) {
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

    /** Total listed domains (the small extra lists may repeat a few from the index). */
    public int size() {
        int n = 0;
        for (DomainLookup b : blocked) n += b.size();
        return n;
    }

    public boolean isBlocked(String domain) {
        if (domain == null || domain.isEmpty()) return false;
        if (matches(allowed, domain)) return false;
        for (DomainLookup b : blocked) if (matches(b, domain)) return true;
        return blockGenerated && GeneratedDomain.looksGenerated(domain);
    }

    /** True if the domain or a parent is on the allowlist. */
    public boolean isAllowed(String domain) {
        return domain != null && !domain.isEmpty() && matches(allowed, domain);
    }

    /** Checks the domain and each parent ("a.b.c", "b.c", "c") without creating substrings. */
    private static boolean matches(DomainLookup set, String domain) {
        if (set.size() == 0) return false;
        int start = 0;
        while (true) {
            if (set.contains(domain, start)) return true;
            int dot = domain.indexOf('.', start);
            if (dot < 0) return false;
            start = dot + 1;
        }
    }

}
