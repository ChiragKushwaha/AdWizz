package rocks.chirag.adwizz.data

/** Which switch controls a list: ad lists are picked one by one, the rest are switched as a group. */
enum class SourceGroup { ADS, TRACKERS, SECURITY, BYPASS }

/** [urls] are mirrors of the same file, tried in order. */
data class BlocklistSource(
    val id: String,
    val name: String,
    val urls: List<String>,
    val description: String,
    val group: SourceGroup = SourceGroup.ADS,
)

object Sources {
    private fun hagezi(file: String) = listOf(
        "https://codeberg.org/hagezi/mirror2/raw/branch/main/dns-blocklists/domains/$file",
        "https://gitlab.com/hagezi/mirror/-/raw/main/dns-blocklists/domains/$file",
    )

    private fun native(vendor: String, name: String) = BlocklistSource(
        "native_$vendor", "$name telemetry", hagezi("native.$vendor.txt"),
        "Built-in tracking by $name", SourceGroup.TRACKERS
    )

    val ALL = listOf(
        // Ads (each one switchable)
        BlocklistSource(
            "stevenblack", "StevenBlack Unified",
            listOf("https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"),
            "Ads + malware, well curated, very few false positives"
        ),
        BlocklistSource(
            "adguard_dns", "AdGuard DNS filter",
            listOf("https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt"),
            "Strong on mobile ad SDKs and trackers"
        ),
        BlocklistSource(
            "hagezi_pro", "HaGeZi Pro", hagezi("pro.txt"),
            "Broad ads, trackers, pop-ups and redirect ad networks"
        ),
        // Off by default: also blocks some app back-ends, e.g. graph.instagram.com (Instagram log-ins and sharing).
        BlocklistSource(
            "hagezi_proplus", "HaGeZi Pro++", hagezi("pro.plus.txt"),
            "Stricter Pro: catches more trackers, but may break some app features"
        ),
        BlocklistSource(
            "oisd_big", "OISD Big",
            listOf("https://big.oisd.nl/domainswild2"),
            "Ads and trackers merged from dozens of lists, tuned not to break apps"
        ),
        BlocklistSource(
            "adguard_mobile", "AdGuard Mobile Ads",
            listOf("https://filters.adtidy.org/extension/ublock/filters/11.txt"),
            "Ad networks built into Android apps and games"
        ),

        // "Block trackers & analytics scripts"
        BlocklistSource(
            "easyprivacy", "EasyPrivacy",
            listOf("https://v.firebog.net/hosts/Easyprivacy.txt"),
            "Analytics, tracking pixels and fingerprinting scripts", SourceGroup.TRACKERS
        ),
        BlocklistSource(
            "frogeye_firstparty", "Hidden first-party trackers",
            listOf("https://hostfiles.frogeye.fr/firstparty-trackers-hosts.txt"),
            "Trackers disguised as the site's own subdomain", SourceGroup.TRACKERS
        ),
        BlocklistSource(
            "frogeye_multiparty", "Third-party trackers",
            listOf("https://hostfiles.frogeye.fr/multiparty-trackers-hosts.txt"),
            "Cross-site trackers and tracking-link redirects", SourceGroup.TRACKERS
        ),
        native("samsung", "Samsung"),
        native("xiaomi", "Xiaomi"),
        native("huawei", "Huawei"),
        native("oppo-realme", "Oppo/Realme"),
        native("vivo", "Vivo"),
        native("tiktok", "TikTok"),
        native("amazon", "Amazon"),
        native("apple", "Apple"),

        // "Block malicious & crypto-mining scripts"
        BlocklistSource(
            "phishing_army", "Phishing Army",
            listOf("https://phishing.army/download/phishing_army_blocklist.txt"),
            "Phishing and scam sites", SourceGroup.SECURITY
        ),
        BlocklistSource(
            "nocoin", "NoCoin",
            listOf("https://raw.githubusercontent.com/hoshsadiq/adblock-nocoin-list/master/hosts.txt"),
            "Scripts that mine crypto-currency in your browser", SourceGroup.SECURITY
        ),

        // Part of "Block DNS bypass"
        BlocklistSource(
            "hagezi_doh", "Encrypted DNS servers", hagezi("doh.txt"),
            "DNS-over-HTTPS servers apps use to skip filtering", SourceGroup.BYPASS
        ),
    )
    val ADS = ALL.filter { it.group == SourceGroup.ADS }
    val DEFAULT_ENABLED = setOf("stevenblack", "adguard_dns", "hagezi_pro", "oisd_big", "adguard_mobile")

    /** Bump when DEFAULT_ENABLED gains sources, so existing installs get them turned on too. */
    const val REVISION = 4
    /** Sources that no longer exist upstream. */
    val REMOVED = setOf("hagezi_popup")
}

data class DnsPreset(val name: String, val primary: String, val secondary: String)

object DnsPresets {
    val ALL = listOf(
        DnsPreset("Cloudflare", "1.1.1.1", "1.0.0.1"),
        DnsPreset("Google", "8.8.8.8", "8.8.4.4"),
        DnsPreset("Quad9", "9.9.9.9", "149.112.112.112"),
        DnsPreset("AdGuard", "94.140.14.14", "94.140.15.15"),
    )
}
