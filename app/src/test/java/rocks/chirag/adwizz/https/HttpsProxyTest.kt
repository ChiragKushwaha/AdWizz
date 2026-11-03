package rocks.chirag.adwizz.https

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.Socket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.zip.GZIPOutputStream
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import kotlin.concurrent.thread

/**
 * HTTPS filtering end to end, on the JVM: a "browser" connects through the real proxy to a fake
 * www.youtube.com. Two test CAs stand in for AdWizz's CA (trusted by the browser) and the public
 * CA that signs the real site (trusted by the proxy).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class HttpsProxyTest {
    private class Ca(name: String) {
        val keys: KeyPair = ec()
        val signer = Signer { d -> Signature.getInstance("SHA256withECDSA").run { initSign(keys.private); update(d); sign() } }
        val cert: X509Certificate = CertBuilder.ca(name, keys.public, signer)
        fun serverContext(host: String): SSLContext {
            val siteKeys = ec()
            val leaf = CertBuilder.leaf(host, siteKeys.public, cert, signer)
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setKeyEntry("k", siteKeys.private, CharArray(0), arrayOf(leaf, cert)) }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(ks, CharArray(0)) }
            return SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, null, null) }
        }
        fun trusting(): SSLContext {
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null); setCertificateEntry("ca", cert) }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(ks) }
            return SSLContext.getInstance("TLS").apply { init(null, tmf.trustManagers, null) }
        }
        companion object { fun ec() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair() }
    }

    private val adwizz = Ca("AdWizz HTTPS filter")
    private val publicCa = Ca("Public CA")
    private val site = publicCa.serverContext("www.youtube.com").serverSocketFactory
        .createServerSocket(0, 50, InetAddress.getByName("127.0.0.1")) as SSLServerSocket
    private val requestsSeen = mutableListOf<String>()
    private var trusted = true
    private lateinit var proxy: HttpsProxy

    @Before fun start() {
        thread(isDaemon = true) { while (true) { val s = runCatching { site.accept() }.getOrNull() ?: break; thread(isDaemon = true) { serveSite(s) } } }
        val certs = object : SiteCerts {
            private val cache = HashMap<String, SSLContext>()
            override fun contextFor(host: String) = synchronized(cache) { cache.getOrPut(host) { adwizz.serverContext(host) } }
            override fun isTrusted() = trusted
        }
        // The proxy reaches "www.youtube.com" at our local server and checks it against the public CA.
        val upstream = Upstream { host, _, secure ->
            val raw = Socket("127.0.0.1", site.localPort)
            if (!secure) raw else (publicCa.trusting().socketFactory.createSocket(raw, host, 443, true) as SSLSocket).apply {
                sslParameters = sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
                startHandshake()
            }
        }
        proxy = HttpsProxy(certs, blocked = { it.endsWith("doubleclick.net") }, onAdUrl = {}, upstream = upstream).also { it.start() }
    }

    @After fun stop() { proxy.stop(); site.close() }

    /** The fake YouTube: player JSON (gzip, chunked) with ads, a page, and an ad ping it should never see. */
    private fun serveSite(s: Socket) = s.use {
        val input = s.getInputStream(); val out = s.getOutputStream()
        while (true) {
            val req = Http.readHead(input) ?: return
            synchronized(requestsSeen) { requestsSeen += req.line }
            val path = req.line.split(' ')[1]
            val (type, body) = when {
                path.startsWith("/youtubei/v1/player") -> "application/json" to """{"adPlacements":[{"ad":1}],"playerAds":[2],"videoDetails":{"title":"Song"}}"""
                path == "/page" -> "text/html" to "<html><head><title>t</title></head><body>hi</body></html>"
                else -> "text/plain" to "plain"
            }
            val gz = req["Accept-Encoding"]?.contains("gzip") == true
            val bytes = if (gz) ByteArrayOutputStream().also { GZIPOutputStream(it).use { z -> z.write(body.toByteArray()) } }.toByteArray() else body.toByteArray()
            val head = StringBuilder("HTTP/1.1 200 OK\r\nContent-Type: $type\r\nTransfer-Encoding: chunked\r\n")
            if (gz) head.append("Content-Encoding: gzip\r\n")
            out.write(head.append("\r\n").toString().toByteArray())
            out.write("${Integer.toHexString(bytes.size)}\r\n".toByteArray()); out.write(bytes); out.write("\r\n0\r\n\r\n".toByteArray())
            out.flush()
        }
    }

    private val chrome = "Mozilla/5.0 (Linux; Android 17; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Mobile Safari/537.36"

    /** Opens a CONNECT tunnel to www.youtube.com through the proxy, then TLS trusting [trust]. */
    private fun browser(trust: SSLContext, ua: String = chrome, host: String = "www.youtube.com"): Pair<String, SSLSocket?> {
        val s = Socket("127.0.0.1", proxy.port).apply { soTimeout = 10_000 }
        s.getOutputStream().write("CONNECT $host:443 HTTP/1.1\r\nHost: $host:443\r\nUser-Agent: $ua\r\n\r\n".toByteArray())
        val status = Http.readHead(s.getInputStream())!!.line
        if (!status.contains("200")) return status to null
        val tls = trust.socketFactory.createSocket(s, host, 443, true) as SSLSocket
        tls.sslParameters = tls.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
        tls.startHandshake()
        return status to tls
    }

    private fun get(tls: SSLSocket, path: String, accept: String = "*/*"): Pair<HttpHead, String> {
        tls.outputStream.write("GET $path HTTP/1.1\r\nHost: www.youtube.com\r\nAccept-Encoding: gzip, br\r\nAccept: $accept\r\n\r\n".toByteArray())
        val input: InputStream = tls.inputStream
        val head = Http.readHead(input)!!
        var body = if (Http.responseHasBody("GET", head.line.split(' ')[1].toInt())) Http.readBody(head, input, true, 1 shl 20) else ByteArray(0)
        if (head["Content-Encoding"] == "gzip") body = HttpsRules.gunzip(body)
        return head to String(body)
    }

    @Test fun browserTrafficIsDecryptedAndYoutubeAdsRemoved() {
        val (_, tls) = browser(adwizz.trusting())
        assertEquals("AdWizz HTTPS filter", (tls!!.session.peerCertificates[1] as X509Certificate).subjectX500Principal.name.substringAfter("CN=").substringBefore(','))
        val (head, body) = get(tls, "/youtubei/v1/player?key=1")
        assertNull(head["Content-Encoding"])                       // decoded for the browser
        assertFalse(body.contains("\"adPlacements\""))
        assertFalse(body.contains("\"playerAds\""))
        assertTrue(body.contains("\"videoDetails\""))
        // Same connection, next request: an ad ping is answered by AdWizz and never reaches the site.
        val (ping, _) = get(tls, "/api/stats/ads?ver=2")
        assertTrue(ping.line.contains("204"))
        assertTrue(synchronized(requestsSeen) { requestsSeen.none { it.contains("/api/stats/ads") } })
        // Pages get the hiding style sheet.
        val (_, page) = get(tls, "/page", accept = "text/html,application/xhtml+xml")
        assertTrue(page.contains("<style id=\"adwizz\">"))
        tls.close()
    }

    @Test fun appsAreTunnelledUntouched() {
        // An app (not a browser) keeps talking to the real site with the real certificate.
        val (_, tls) = browser(publicCa.trusting(), ua = "com.google.android.youtube/21.43 (Linux; U; Android 17)")
        val (_, body) = get(tls!!, "/youtubei/v1/player")
        assertTrue(body.contains("\"adPlacements\""))   // untouched: the add-on handles the app
        tls.close()
    }

    @Test fun nothingIsDecryptedUntilTheCertificateIsTrusted() {
        trusted = false
        val (_, tls) = browser(publicCa.trusting())
        assertEquals("Public CA", (tls!!.session.peerCertificates[1] as X509Certificate).subjectX500Principal.name.substringAfter("CN=").substringBefore(','))
        tls.close()
    }

    @Test fun blockedHostsAreRefused() {
        val (status, tls) = browser(adwizz.trusting(), host = "googleads.g.doubleclick.net")
        assertTrue(status.contains("403"))
        assertNull(tls)
    }
}
