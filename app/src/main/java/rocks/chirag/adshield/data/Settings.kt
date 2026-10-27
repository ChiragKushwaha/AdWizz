package rocks.chirag.adshield.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

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
    val DEFAULT_ENABLED = setOf("stevenblack", "adguard_dns", "hagezi_pro")

    /** Bump when DEFAULT_ENABLED gains sources, so existing installs get them turned on too. */
    const val REVISION = 3
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

class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("adshield", Context.MODE_PRIVATE)

    init {
        // Existing installs saved their old source set; add the newer defaults once.
        if (prefs.getInt("sources_rev", 0) < Sources.REVISION) {
            prefs.edit {
                if (prefs.contains("sources")) {
                    putStringSet("sources", enabledSources + Sources.DEFAULT_ENABLED - Sources.REMOVED)
                }
                putInt("sources_rev", Sources.REVISION)
            }
        }
    }

    /** Whether the user wants protection on (used to restore after reboot / always-on). */
    var wantEnabled: Boolean
        get() = prefs.getBoolean("want_enabled", false)
        set(v) = prefs.edit { putBoolean("want_enabled", v) }

    var primaryDns: String
        get() = prefs.getString("dns1", "1.1.1.1")!!
        set(v) = prefs.edit { putString("dns1", v) }

    var secondaryDns: String
        get() = prefs.getString("dns2", "1.0.0.1")!!
        set(v) = prefs.edit { putString("dns2", v) }

    /** Route well-known public resolvers into the tunnel so apps can't sidestep filtering. */
    var catchHardcodedDns: Boolean
        get() = prefs.getBoolean("catch_hardcoded", true)
        set(v) = prefs.edit { putBoolean("catch_hardcoded", v) }

    var enabledSources: Set<String>
        get() = prefs.getStringSet("sources", Sources.DEFAULT_ENABLED)!!.toSet()
        set(v) = prefs.edit { putStringSet("sources", v) }

    var allowlist: Set<String>
        get() = prefs.getStringSet("allowlist", emptySet())!!.toSet()
        set(v) = prefs.edit { putStringSet("allowlist", v) }

    var customBlocklist: Set<String>
        get() = prefs.getStringSet("custom_block", emptySet())!!.toSet()
        set(v) = prefs.edit { putStringSet("custom_block", v) }

    var lastListUpdate: Long
        get() = prefs.getLong("last_update", 0L)
        set(v) = prefs.edit { putLong("last_update", v) }

    var lifetimeBlocked: Long
        get() = prefs.getLong("lifetime_blocked", 0L)
        set(v) = prefs.edit { putLong("lifetime_blocked", v) }

    /** Extra tracker / analytics-script lists on top of the ad lists. */
    var blockTrackers: Boolean
        get() = prefs.getBoolean("block_trackers", true)
        set(v) = prefs.edit { putBoolean("block_trackers", v) }

    /** Block machine-generated ad domains (e.g. "pgjlctmswgnwf.com") that aren't on any list yet. */
    var blockGenerated: Boolean
        get() = prefs.getBoolean("block_generated", true)
        set(v) = prefs.edit { putBoolean("block_generated", v) }

    /** Phishing and crypto-mining script lists. */
    var blockMalicious: Boolean
        get() = prefs.getBoolean("block_malicious", true)
        set(v) = prefs.edit { putBoolean("block_malicious", v) }

    /** Package names that bypass the VPN entirely, so they can't see it (and aren't filtered). */
    var excludedApps: Set<String>
        get() = prefs.getStringSet("excluded_apps", emptySet())!!.toSet()
        set(v) = prefs.edit { putStringSet("excluded_apps", v) }

    /** "system", "light" or "dark". */
    var themeMode: String
        get() = prefs.getString("theme_mode", "system")!!
        set(v) = prefs.edit { putString("theme_mode", v) }
}
