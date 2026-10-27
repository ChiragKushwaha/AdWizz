package rocks.chirag.adwizz.data

import rocks.chirag.adwizz.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Size-capped HTTPS downloads for the updater. */
internal object UpdateDownload {
    /** Small text download (the manifest). */
    fun text(url: String, maxBytes: Long): String = open(url).use { input ->
        ByteArrayOutputStream().also { copyCapped(input, it, maxBytes) }.toString(Charsets.UTF_8.name())
    }

    /** Downloads [url] to [to], reporting 0..1 progress. GitHub redirects assets to its CDN (https → https). */
    fun file(url: String, to: File, expectedBytes: Long, progress: (Float) -> Unit) {
        val c = connect(url)
        try {
            // contentLength (not ...Long, which needs API 24): updates are far below 2 GB.
            val total = if (c.contentLength > 0) c.contentLength.toLong() else expectedBytes
            c.inputStream.use { input ->
                to.outputStream().use { out ->
                    copyCapped(input, out, UpdateManifest.MAX_APK_BYTES) { done ->
                        if (total > 0) progress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally { c.disconnect() }
    }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun open(url: String): InputStream {
        val c = connect(url)
        return object : java.io.FilterInputStream(c.inputStream) {
            override fun close() { super.close(); c.disconnect() }
        }
    }

    private fun connect(url: String): HttpURLConnection {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = true
            setRequestProperty("User-Agent", "AdWizz/${BuildConfig.VERSION_NAME}")
        }
        if (c.responseCode !in 200..299) { c.disconnect(); error("HTTP ${c.responseCode}") }
        return c
    }

    private fun copyCapped(input: InputStream, out: OutputStream, max: Long, onProgress: (Long) -> Unit = {}) {
        val buf = ByteArray(64 * 1024)
        var done = 0L
        while (true) {
            val n = input.read(buf); if (n < 0) break
            done += n
            if (done > max) error("Download too large")
            out.write(buf, 0, n)
            onProgress(done)
        }
    }
}
