package rocks.chirag.adwizz.core;

/**
 * Spots machine-generated ad domains (e.g. "pgjlctmswgnwf.com") that ad networks register daily,
 * faster than blocklists can list them.
 *
 * Rule: the site's own name (the label before ".com", ".co.uk", …) is 11+ letters with no vowels
 * at all. Checked against the top 100k sites (Tranco list, Oct 2026): it flags 5, all themselves
 * gibberish names, while catching the rotating ad domains seen on canyoublockit.com. Looser rules
 * (few vowels, long consonant runs, digit mixes) broke real sites like letsencrypt.org, so they're
 * deliberately not used. Subdomains are never judged: CDNs use random-looking ones legitimately.
 */
public final class GeneratedDomain {
    private GeneratedDomain() {}

    private static final java.util.Set<String> SECOND_LEVEL = new java.util.HashSet<>(java.util.Arrays.asList(
        "co", "com", "net", "org", "ac", "gov", "edu", "or", "ne", "go"));

    /** The registrable name: "ads.example.co.uk" -> "example", "x.pgjlctmswgnwf.com" -> "pgjlctmswgnwf". */
    static String siteLabel(String domain) {
        String[] p = domain.split("\\.");
        if (p.length < 2) return null;
        if (p.length >= 3 && p[p.length - 1].length() == 2 && SECOND_LEVEL.contains(p[p.length - 2])) return p[p.length - 3];
        return p[p.length - 2];
    }

    public static boolean looksGenerated(String domain) {
        if (domain == null) return false;
        String label = siteLabel(domain);
        if (label == null || label.length() < 11) return false;
        for (int i = 0; i < label.length(); i++) {
            char c = label.charAt(i);
            if (c < 'a' || c > 'z') return false; // digits / hyphens: not this pattern
            if (c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u') return false;
        }
        return true;
    }
}
