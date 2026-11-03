package rocks.chirag.adwizz.https

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * The local proxy behind HTTPS filtering. The VPN tells Android to send web traffic here
 * (127.0.0.1 only; nothing outside the phone can connect). For each connection:
 *  - a blocked host gets "403" straight away (the proxy resolves names, so DNS filtering can't see it);
 *  - a browser talking to a site, with our certificate installed, is decrypted ([MitmSession]);
 *  - everything else (apps, which don't trust user certificates) is tunnelled through untouched.
 *
 * @param blocked returns true if a host is on the blocklists, and records the decision.
 */
@androidx.annotation.RequiresApi(29)
internal class HttpsProxy(
    private val ca: SiteCerts,
    private val blocked: (String) -> Boolean,
    /** A decrypted request was an ad URL on a content host (e.g. YouTube's ad pings): count it. */
    private val onAdUrl: (String) -> Unit,
    private val upstream: Upstream = Upstream.DIRECT,
) {
    private val server = ServerSocket(0, 128, InetAddress.getByName("127.0.0.1"))
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "adwizz-https-${count.incrementAndGet()}").apply { isDaemon = true } }
    private val count = AtomicInteger()
    /** Clients that refused our certificate for a host: tunnelled from then on (key: host + user agent). */
    private val refused = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var trusted = false
    @Volatile private var trustCheckedAt = 0L
    @Volatile private var running = true

    val port: Int get() = server.localPort

    fun start() {
        Thread({
            while (running) {
                val client = try { server.accept() } catch (e: IOException) { break }
                pool.execute { serve(client) }
            }
        }, "adwizz-https").apply { isDaemon = true }.start()
    }

    fun stop() {
        running = false
        runCatching { server.close() }
        pool.shutdownNow()
    }

    private fun serve(client: Socket) {
        client.use { handle(it) }
    }

    private fun handle(client: Socket) {
        try {
            client.soTimeout = 120_000
            val input = client.getInputStream(); val out = client.getOutputStream()
            val head = Http.readHead(input) ?: return
            val parts = head.line.split(' ')
            if (parts.size < 3) return
            if (parts[0].equals("CONNECT", true)) connect(client, head, parts[1], input, out)
            else MitmSession.plain(client, head, blocked, upstream, onAdUrl)   // http:// through the proxy
        } catch (e: IOException) {
            // The browser or site hung up: normal.
        } catch (e: RuntimeException) {
            Log.w(TAG, "Proxy connection failed", e)
        }
    }

    private fun connect(client: Socket, head: HttpHead, target: String, input: InputStream, out: OutputStream) {
        val host = target.substringBeforeLast(':').trim('[', ']').lowercase()
        val port = target.substringAfterLast(':').toIntOrNull() ?: 443
        if (blocked(host)) {
            out.write("HTTP/1.1 403 Forbidden\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray()); return
        }
        out.write("HTTP/1.1 200 Connection established\r\n\r\n".toByteArray()); out.flush()
        val ua = head["User-Agent"].orEmpty()
        if (rocks.chirag.adwizz.BuildConfig.DEBUG) Log.d(TAG, "CONNECT $host browser=${isBrowser(ua)} trusted=${isTrusted()} ua=${ua.take(60)}")
        val key = "$host|${ua.hashCode()}"
        if (port == 443 && isBrowser(ua) && isTrusted() && key !in refused) {
            if (!MitmSession.decrypt(client, host, ca, upstream, onAdUrl)) refused += key
        } else tunnel(client, input, host, port)
    }

    /** Pipes bytes both ways without looking at them. */
    private fun tunnel(client: Socket, input: InputStream, host: String, port: Int) {
        upstream.open(host, port, secure = false).use { up ->
            val t = Thread({ runCatching { up.getInputStream().copyTo(client.getOutputStream(), 64 * 1024) }; runCatching { client.shutdownOutput() } }, "adwizz-tunnel")
            t.isDaemon = true; t.start()
            runCatching { input.copyTo(up.getOutputStream(), 64 * 1024) }
            runCatching { up.shutdownOutput() }
            t.join(120_000)
        }
    }

    private fun isTrusted(): Boolean {
        val now = System.currentTimeMillis()
        if (now - trustCheckedAt > 30_000) { trusted = ca.isTrusted(); trustCheckedAt = now }
        return trusted
    }

    companion object {
        private const val TAG = "AdWizzHttps"

        /** Browsers trust user-installed certificates; apps and in-app web views don't. */
        fun isBrowser(userAgent: String): Boolean =
            userAgent.startsWith("Mozilla/5.0") && "; wv)" !in userAgent &&
                listOf("Chrome/", "Firefox/", "SamsungBrowser/", "EdgA/", "OPR/").any { it in userAgent }
    }
}
