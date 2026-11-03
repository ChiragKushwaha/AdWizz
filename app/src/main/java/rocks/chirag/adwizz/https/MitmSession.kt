package rocks.chirag.adwizz.https

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * One decrypted browser connection: TLS with the browser using our certificate for [host], TLS
 * with the real site (checked as usual), and HTTP/1.1 requests passed between them, filtered by
 * [HttpsRules]. Responses we don't rewrite are streamed through as they arrive.
 */
/** How the proxy reaches the real site. Tests point it at a local server. */
@androidx.annotation.RequiresApi(29)
internal fun interface Upstream {
    fun open(host: String, port: Int, secure: Boolean): Socket

    companion object {
        /** The real site, its certificate and name checked exactly as a browser would. */
        val DIRECT = Upstream { host, port, secure ->
            val raw = Socket().apply { connect(InetSocketAddress(host, port), 15_000); soTimeout = 120_000 }
            if (!secure) return@Upstream raw
            val tls = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw, host, port, true) as SSLSocket
            tls.sslParameters = tls.sslParameters.apply {
                endpointIdentificationAlgorithm = "HTTPS"
                applicationProtocols = arrayOf("http/1.1")
            }
            tls.startHandshake()
            tls
        }
    }
}

@androidx.annotation.RequiresApi(29)
internal object MitmSession {
    private const val TAG = "AdWizzHttps"
    private const val MAX_REWRITE = 12 * 1024 * 1024

    /** Returns false if the browser rejected our certificate (then that host is tunnelled next time). */
    fun decrypt(client: Socket, host: String, ca: SiteCerts, upstream: Upstream, onAdUrl: (String) -> Unit): Boolean {
        val tls = ca.contextFor(host).socketFactory.createSocket(client, host, client.port, false) as SSLSocket
        tls.useClientMode = false
        tls.sslParameters = tls.sslParameters.apply { applicationProtocols = arrayOf("http/1.1") }
        try {
            tls.startHandshake()
        } catch (e: SSLException) {
            Log.i(TAG, "Browser refused our certificate for $host: ${e.message}")
            return false
        }
        tls.use { exchange(it.inputStream, it.outputStream, host, 443, secure = true, upstream, onAdUrl) }
        return true
    }

    /** A plain http:// request sent to the proxy with an absolute URL. */
    fun plain(client: Socket, first: HttpHead, blocked: (String) -> Boolean, upstream: Upstream, onAdUrl: (String) -> Unit) {
        val url = first.line.split(' ')[1]
        val hostPort = url.substringAfter("://").substringBefore('/')
        val host = hostPort.substringBefore(':').lowercase()
        if (blocked(host)) { forbidden(client.getOutputStream()); return }
        exchange(client.getInputStream(), client.getOutputStream(), host, hostPort.substringAfter(':', "80").toIntOrNull() ?: 80,
            secure = false, upstream, onAdUrl, first)
    }

    private fun exchange(
        cIn: InputStream, cOut: OutputStream, host: String, port: Int, secure: Boolean,
        upstreamFor: Upstream, onAdUrl: (String) -> Unit, pending: HttpHead? = null,
    ) {
        var upstream: Socket? = null
        try {
            var next = pending
            while (true) {
                val req = next ?: Http.readHead(cIn) ?: return
                next = null
                val parts = req.line.split(' ')
                if (parts.size < 3) return
                val method = parts[0]
                val path = parts[1].let { if (it.startsWith("http")) "/" + it.substringAfter("://").substringAfter('/', "") else it }
                req.line = "$method $path ${parts[2]}"
                req.remove("Proxy-Connection")
                if (HttpsRules.isBlockedUrl(host, path)) {
                    onAdUrl(host)
                    Http.copyBody(req, cIn, Discard, isResponse = false)
                    cOut.write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n".toByteArray()); cOut.flush()
                    continue
                }
                val rewrite = HttpsRules.wantsRewrite(host, path, req["Accept"], req["Sec-Fetch-Dest"])
                if (rewrite) req.set("Accept-Encoding", "gzip")   // never brotli: we must be able to decode it
                val up = upstream ?: upstreamFor.open(host, port, secure).also { upstream = it }
                val uOut = up.getOutputStream(); val uIn = up.getInputStream()
                req.write(uOut)
                Http.copyBody(req, cIn, uOut, isResponse = false)
                uOut.flush()
                var resp = Http.readHead(uIn) ?: return
                while (status(resp) == 100) { resp.write(cOut); resp = Http.readHead(uIn) ?: return }
                if (status(resp) == 101) { resp.write(cOut); cOut.flush(); pipe(cIn, uOut, uIn, cOut); return }
                val keep = respond(resp, uIn, cOut, method, host, rewrite)
                if (!keep || req.wantsClose || resp.wantsClose) return
            }
        } finally {
            runCatching { upstream?.close() }
        }
    }

    /** Sends the response on, rewritten if it's YouTube data or a page. Returns false to close. */
    private fun respond(resp: HttpHead, uIn: InputStream, cOut: OutputStream, method: String, host: String, rewrite: Boolean): Boolean {
        val hasBody = Http.responseHasBody(method, status(resp))
        val encoding = resp["Content-Encoding"]?.lowercase()
        if (rewrite && hasBody && HttpsRules.canRewrite(resp["Content-Type"]) && (encoding == null || encoding == "gzip" || encoding == "identity")) {
            var body = Http.readBody(resp, uIn, isResponse = true, max = MAX_REWRITE)
            if (encoding == "gzip") body = HttpsRules.gunzip(body)
            body = HttpsRules.rewrite(host, resp["Content-Type"], body)
            resp.remove("Content-Encoding"); resp.remove("Transfer-Encoding"); resp.remove("Content-Length")
            resp.set("Content-Length", body.size.toString())
            resp.write(cOut); cOut.write(body); cOut.flush()
            return true
        }
        resp.write(cOut)
        if (!hasBody) { cOut.flush(); return true }
        return Http.copyBody(resp, uIn, cOut, isResponse = true)
    }

    /** WebSockets and other upgrades: raw bytes both ways. */
    private fun pipe(cIn: InputStream, uOut: OutputStream, uIn: InputStream, cOut: OutputStream) {
        val t = Thread({ runCatching { uIn.copyTo(cOut, 16 * 1024) } }, "adwizz-ws").apply { isDaemon = true; start() }
        runCatching { cIn.copyTo(uOut, 16 * 1024) }
        t.join(1000)
    }

    private object Discard : OutputStream() {
        override fun write(b: Int) = Unit
        override fun write(b: ByteArray, off: Int, len: Int) = Unit
    }

    private fun status(h: HttpHead) = h.line.split(' ').getOrNull(1)?.toIntOrNull() ?: 0

    private fun forbidden(out: OutputStream) {
        try { out.write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()) } catch (_: IOException) { }
    }
}
