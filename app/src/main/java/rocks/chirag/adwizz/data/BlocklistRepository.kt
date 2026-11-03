package rocks.chirag.adwizz.data

import rocks.chirag.adwizz.tr
import android.content.Context
import android.util.Log
import rocks.chirag.adwizz.BuildConfig
import rocks.chirag.adwizz.core.DomainLookup
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
    private val listDir = File(context.filesDir, "lists").apply { mkdirs() }
    private val downloader = BlocklistDownloader(listDir)
    private val adsIndex = sharedIndex(listDir, "ads")
    private val trackersIndex = sharedIndex(listDir, "trackers")
    private val securityIndex = sharedIndex(listDir, "security")

    init {
        // Before 1.5 everything lived in one database; it's now split by category (~24 MB freed).
        File(listDir, "blocked.idx").delete(); File(listDir, "blocked.key").delete()
    }

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
        // The big lists live in on-device databases (memory-mapped files, not the heap), one per
        // category so the dashboard can tell an ad from a tracker.
        fun input(id: String): BlocklistIndex.Input? = fileFor(id).takeIf { it.exists() && it.length() > 0 }
            ?.let { f -> BlocklistIndex.Input(id, BlocklistIndex.version(f)) { f.reader() } }
        fun asset(id: String, name: String) =
            BlocklistIndex.Input(id, "app${BuildConfig.VERSION_CODE}") { context.assets.open(name).reader() }
        val active = activeSources()
        fun inputsFor(vararg groups: SourceGroup) = active.filter { it.group in groups }.mapNotNull { input(it.id) }

        val ads = adsIndex.get(listOf(asset("starter", "starter_blocklist.txt")) + inputsFor(SourceGroup.ADS))
        val trackers = trackersIndex.get(
            (if (settings.blockTrackers) listOf(asset("starter_trackers", "starter_trackers.txt")) else emptyList()) +
                inputsFor(SourceGroup.TRACKERS)
        )
        val security = securityIndex.get(inputsFor(SourceGroup.SECURITY, SourceGroup.BYPASS))
        // Small lists that change often stay in memory: the user's own blocks and "Block Reels & Shorts".
        val extra = DomainSet()
        if (settings.blockShortVideos) ShortVideoRules.DOMAINS.forEach { extra.add(it) }
        if (settings.blockSponsored) SponsoredRules.DOMAINS.forEach { extra.add(it) }
        ListParser.normalise(settings.customBlocklist).forEach { extra.add(it) }
        val allowed = DomainSet().apply { ListParser.normalise(settings.allowlist).forEach { add(it) } }
        // Checked in this order (BlockCategory.forLookup's order): the big ad lists also carry many
        // trackers, so threats and trackers go first and each block lands in its truest category.
        return DomainMatcher(arrayOf<DomainLookup>(extra, security, trackers, ads), allowed)
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
            return if (failed.isEmpty()) tr("lists.result_ok", "count" to ok) else tr("lists.result_failed", "count" to ok, "lists" to failed.joinToString())
        } finally {
            VpnState.setUpdating(false)
        }
    }

    companion object {
        private const val TAG = "Blocklist"
        private val indexes = HashMap<String, BlocklistIndex>()

        /** One database per folder, shared by every repository (the VPN, the settings screen…). */
        @Synchronized internal fun sharedIndex(dir: File, name: String) =
            indexes.getOrPut("${dir.absolutePath}/$name") { BlocklistIndex(dir, name) }
        /** Ad networks rotate domains constantly and the lists update daily or more. */
        internal const val STALE_MS = 12L * 60 * 60 * 1000
        internal const val STALE_METERED_MS = 2L * 24 * 60 * 60 * 1000
    }
}
