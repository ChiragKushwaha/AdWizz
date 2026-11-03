package rocks.chirag.adwizz.data

import rocks.chirag.adwizz.core.DomainMatcher

/** Why a look-up was blocked, for the dashboard. */
enum class BlockCategory {
    /** Ad networks, plus the user's own blocks and machine-generated ad domains. */
    ADS,
    /** Analytics, tracking pixels, tracking-link redirects, phone-maker telemetry. */
    TRACKERS,
    /** Phishing, malware, crypto-mining and DNS-bypass servers. */
    THREATS,
    /** The app's internet is switched off in "Block apps' internet". */
    FIREWALL;

    companion object {
        /** Maps DomainMatcher.blockedBy() to a category (lookups: extra, security, trackers, ads). */
        fun forLookup(index: Int): BlockCategory? = when (index) {
            DomainMatcher.NOT_BLOCKED -> null
            1 -> THREATS
            2 -> TRACKERS
            else -> ADS   // the user's own blocks, ad lists, machine-generated ad names
        }
    }
}
