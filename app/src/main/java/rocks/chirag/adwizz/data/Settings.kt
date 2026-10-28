package rocks.chirag.adwizz.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class Settings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("adwizz", Context.MODE_PRIVATE)

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

    /** Leave Reels / Shorts screens (needs the accessibility service switched on too). */
    var blockShortVideos: Boolean
        get() = prefs.getBoolean("block_short_videos", false)
        set(v) = prefs.edit { putBoolean("block_short_videos", v) }

    /** "system", "light" or "dark". */
    var themeMode: String
        get() = prefs.getString("theme_mode", "system")!!
        set(v) = prefs.edit { putString("theme_mode", v) }
}
