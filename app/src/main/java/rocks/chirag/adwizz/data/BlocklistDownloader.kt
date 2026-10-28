package rocks.chirag.adwizz.data

import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Downloads blocklists into [dir] as "<id>.txt", trying each mirror in turn. */
internal class BlocklistDownloader(private val dir: File) {

    fun fileFor(id: String) = File(dir, "$id.txt")

    /** Tries each mirror; throws the last error if all fail. */
    fun download(src: BlocklistSource) {
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

    /** Where the server's ETag / Last-Modified for [id] are kept, so unchanged lists aren't re-sent. */
    private fun tagFile(id: String) = File(dir, "$id.etag")

    private fun download(url: String, id: String) {
        val list = fileFor(id)
        val tags = tagFile(id).takeIf { list.exists() && list.length() > 0 && it.exists() }?.readLines().orEmpty()
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "AdWizz")
            tags.getOrNull(0)?.takeIf { it.isNotEmpty() }?.let { setRequestProperty("If-None-Match", it) }
            tags.getOrNull(1)?.takeIf { it.isNotEmpty() }?.let { setRequestProperty("If-Modified-Since", it) }
        }
        try {
            // Unchanged since last time: keep the file we have (a few bytes instead of megabytes).
            // (The file is left untouched, so the on-device database isn't rebuilt for nothing.)
            if (conn.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) return
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val tmp = File(dir, "$id.tmp")
            conn.inputStream.use { input -> tmp.outputStream().use { out -> copyCapped(input, out, tmp) } }
            if (tmp.length() < 1024) { tmp.delete(); error("List too small, ignoring") }
            tmp.renameTo(list)
            val etag = conn.getHeaderField("ETag").orEmpty()
            val modified = conn.getHeaderField("Last-Modified").orEmpty()
            if (etag.isEmpty() && modified.isEmpty()) tagFile(id).delete()
            else tagFile(id).writeText("$etag\n$modified")
        } finally {
            conn.disconnect()
        }
    }

    /** Hard cap: a broken or hostile mirror can't fill the disk / memory. */
    private fun copyCapped(input: java.io.InputStream, out: java.io.OutputStream, tmp: File) {
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

    companion object {
        private const val TAG = "Blocklist"
        /** Largest list we accept (HaGeZi Pro++, the biggest, is ~14 MB). */
        const val MAX_LIST_BYTES = 64L * 1024 * 1024
    }
}
