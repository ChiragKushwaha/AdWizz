package rocks.chirag.adwizz.data

import android.content.Context
import android.util.Log
import rocks.chirag.adwizz.core.DomainMatcher
import rocks.chirag.adwizz.core.DomainSet
import rocks.chirag.adwizz.core.ListParser
import rocks.chirag.adwizz.focus.ShortVideoRules
import java.io.File

/** Loads the bundled starter list plus downloaded lists, and refreshes downloads on demand. */
class BlocklistRepository(context: Context) {
    // Application context only: callers may pass an Activity and outlive it (slow downloads).
    private val context: Context = context.applicationContext
    private val settings = Settings(context)
    private val downloader = BlocklistDownloader(File(context.filesDir, "lists").apply { mkdirs() })

    private fun fileFor(id: String) = downloader.fileFor(id)

    /** Lists in use: the chosen ad lists plus every list of each group that's switched on. */
    fun activeSources(): List<BlocklistSource> = Sources.ALL.filter {
        when (it.group) {
            SourceGroup.ADS -> it.id in settings.enabledSources
            SourceGroup.TRACKERS -> settings.blockTrackers
            SourceGroup.SECURITY -> settings.blockMalicious
            SourceGroup.BYPASS -> settings.catchHardcodedDns
        }
    }

    /**
     * True if an active list was never downloaded, or the lists are stale: older than 12 hours on
     * Wi-Fi, or 2 days on mobile data (about 30 MB a refresh, so it goes easy on data plans).
     */
    fun needsDownload(metered: Boolean = isMetered()): Boolean =
        activeSources().map { it.id }.any { fileFor(it).let { f -> !f.exists() || f.length() == 0L } } ||
            System.currentTimeMillis() - settings.lastListUpdate > (if (metered) STALE_METERED_MS else STALE_MS)

    /** AdWizz is left out of its own VPN, so this is the real Wi-Fi / mobile network. */
    private fun isMetered(): Boolean = runCatching {
        context.getSystemService(android.net.ConnectivityManager::class.java)?.isActiveNetworkMetered
    }.getOrNull() ?: true

    /** Builds a matcher from everything available on disk. Safe to call from any background thread. */
    fun buildMatcher(): DomainMatcher = loadMatcher()

    private fun loadMatcher(): DomainMatcher {
        // Sized for ~1M domains up front so big lists load without rehashing.
        val blocked = DomainSet(1_000_000)
        context.assets.open("starter_blocklist.txt").bufferedReader().use { ListParser.parseInto(it, blocked) }
        if (settings.blockTrackers) {
            context.assets.open("starter_trackers.txt").bufferedReader().use { ListParser.parseInto(it, blocked) }
        }
        for (id in activeSources().map { it.id }) {
            val f = fileFor(id)
            if (f.exists()) runCatching { f.bufferedReader().use { ListParser.parseInto(it, blocked) } }
                .onFailure { Log.w(TAG, "Failed to parse $id", it) }
        }
        // "Block Reels & Shorts" also cuts off short-video-only services (TikTok etc.) everywhere.
        if (settings.blockShortVideos) ShortVideoRules.DOMAINS.forEach { blocked.add(it) }
        ListParser.normalise(settings.customBlocklist).forEach { blocked.add(it) }
        val allowed = DomainSet().apply { ListParser.normalise(settings.allowlist).forEach { add(it) } }
        return DomainMatcher(blocked.trim(), allowed)
            .withGeneratedDomainBlocking(settings.blockGenerated)
            .also {
                VpnState.setListSize(it.size())
                Telemetry.crashContext("list_size", it.size().toString())
            }
    }

    /** Downloads every enabled source. Returns a short human-readable result. Blocking — call off main thread. */
    fun downloadAll(): String {
        VpnState.setUpdating(true)
        try {
            var ok = 0
            val failed = mutableListOf<String>()
            for (src in activeSources()) {
                try {
                    downloader.download(src)
                    ok++
                } catch (e: Exception) {
                    Log.w(TAG, "Download failed for ${src.id}", e)
                    failed += src.name
                }
            }
            if (ok > 0) settings.lastListUpdate = System.currentTimeMillis()
            Telemetry.event(context, "lists_updated", "ok" to ok, "failed" to failed.size)
            return if (failed.isEmpty()) "Updated $ok list(s)" else "Updated $ok, failed: ${failed.joinToString()}"
        } finally {
            VpnState.setUpdating(false)
        }
    }

    companion object {
        private const val TAG = "Blocklist"
        /** Ad networks rotate domains constantly and the lists update daily or more. */
        internal const val STALE_MS = 12L * 60 * 60 * 1000
        internal const val STALE_METERED_MS = 2L * 24 * 60 * 60 * 1000
    }
}
