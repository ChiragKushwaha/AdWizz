package rocks.chirag.adshield.data

import android.content.Context
import android.util.Log
import rocks.chirag.adshield.core.DomainMatcher
import rocks.chirag.adshield.core.DomainSet
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Loads the bundled starter list plus downloaded lists, and refreshes downloads on demand. */
class BlocklistRepository(context: Context) {
    // Application context only: callers may pass an Activity and outlive it (slow downloads).
    private val context: Context = context.applicationContext
    private val settings = Settings(context)
    private val dir = File(context.filesDir, "lists").apply { mkdirs() }

    private fun fileFor(id: String) = File(dir, "$id.txt")

    /** Lists in use: the chosen ad lists plus every list of each group that's switched on. */
    fun activeSources(): List<BlocklistSource> = Sources.ALL.filter {
        when (it.group) {
            SourceGroup.ADS -> it.id in settings.enabledSources
            SourceGroup.TRACKERS -> settings.blockTrackers
            SourceGroup.SECURITY -> settings.blockMalicious
            SourceGroup.BYPASS -> settings.catchHardcodedDns
        }
    }

    /** True if an active list was never downloaded, or the lists are older than [STALE_MS]. */
    fun needsDownload(): Boolean =
        activeSources().map { it.id }.any { fileFor(it).let { f -> !f.exists() || f.length() == 0L } } ||
            System.currentTimeMillis() - settings.lastListUpdate > STALE_MS

    /** Builds a matcher from everything available on disk. Safe to call from any background thread. */
    fun buildMatcher(): DomainMatcher {
        // Sized for ~1M domains up front so big lists load without rehashing.
        val blocked = DomainSet(1_000_000)
        context.assets.open("starter_blocklist.txt").bufferedReader().use { DomainMatcher.parseInto(it, blocked) }
        if (settings.blockTrackers) {
            context.assets.open("starter_trackers.txt").bufferedReader().use { DomainMatcher.parseInto(it, blocked) }
        }
        for (id in activeSources().map { it.id }) {
            val f = fileFor(id)
            if (f.exists()) runCatching { f.bufferedReader().use { DomainMatcher.parseInto(it, blocked) } }
                .onFailure { Log.w(TAG, "Failed to parse $id", it) }
        }
        DomainMatcher.normalise(settings.customBlocklist).forEach { blocked.add(it) }
        val allowed = DomainSet().apply { DomainMatcher.normalise(settings.allowlist).forEach { add(it) } }
        return DomainMatcher(blocked.trim(), allowed)
            .withGeneratedDomainBlocking(settings.blockGenerated)
            .also { VpnState.setListSize(it.size()) }
    }

    /** Downloads every enabled source. Returns a short human-readable result. Blocking — call off main thread. */
    fun downloadAll(): String {
        VpnState.setUpdating(true)
        try {
            var ok = 0
            val failed = mutableListOf<String>()
            for (src in activeSources()) {
                try {
                    download(src)
                    ok++
                } catch (e: Exception) {
                    Log.w(TAG, "Download failed for ${src.id}", e)
                    failed += src.name
                }
            }
            if (ok > 0) settings.lastListUpdate = System.currentTimeMillis()
            return if (failed.isEmpty()) "Updated $ok list(s)" else "Updated $ok, failed: ${failed.joinToString()}"
        } finally {
            VpnState.setUpdating(false)
        }
    }

    /** Tries each mirror in turn; throws the last error if all fail. */
    private fun download(src: BlocklistSource) {
        var last: Exception? = null
        for (url in src.urls) {
            try {
                download(url, src.id)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Mirror failed for ${src.id}: $url", e)
                last = e
            }
        }
        throw last ?: IllegalStateException("No URL for ${src.id}")
    }

    private fun download(url: String, id: String) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "AdWizz/1.0")
        }
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val tmp = File(dir, "$id.tmp")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    // Hard cap: a broken or hostile mirror can't fill the disk / memory.
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_LIST_BYTES) { tmp.delete(); error("List larger than ${MAX_LIST_BYTES shr 20} MB") }
                        out.write(buf, 0, n)
                    }
                }
            }
            if (tmp.length() < 1024) { tmp.delete(); error("List too small, ignoring") }
            tmp.renameTo(fileFor(id))
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TAG = "Blocklist"
        /** Largest list we accept (HaGeZi Pro, the biggest, is ~12 MB). */
        const val MAX_LIST_BYTES = 64L * 1024 * 1024
        /** Ad networks rotate domains constantly, so refresh lists every 2 days. */
        private const val STALE_MS = 2L * 24 * 60 * 60 * 1000
    }
}
