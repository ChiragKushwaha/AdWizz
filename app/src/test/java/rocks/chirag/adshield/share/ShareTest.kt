package rocks.chirag.adshield.share

import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import rocks.chirag.adshield.share.ShareContent.Platform
import rocks.chirag.adshield.ui.qrMatrix
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

class ShareContentTest {

    private val iphone = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148"
    private val ipad = "Mozilla/5.0 (iPad; CPU OS 16_0 like Mac OS X)"
    private val mac = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 Safari/605.1.15"
    private val android = "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 Chrome/120 Mobile"
    private val windows = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120"

    @Test fun detectsPlatforms() {
        assertEquals(Platform.IOS, ShareContent.detect(iphone))
        assertEquals(Platform.IOS, ShareContent.detect(ipad))
        assertEquals(Platform.MAC, ShareContent.detect(mac))
        assertEquals(Platform.ANDROID, ShareContent.detect(android))
        assertEquals(Platform.WINDOWS, ShareContent.detect(windows))
        assertEquals(Platform.OTHER, ShareContent.detect("curl/8.0"))
        assertEquals(Platform.OTHER, ShareContent.detect(null))
        assertEquals(Platform.OTHER, ShareContent.detect(""))
    }

    @Test fun routesIndexAndProfile() {
        val index = ShareContent.route("GET", "/", iphone)
        assertEquals(200, index.status)
        assertTrue(index.contentType.startsWith("text/html"))
        assertEquals(200, ShareContent.route("GET", "/index.html?x=1", null).status)

        val profile = ShareContent.route("GET", ShareContent.PROFILE_PATH, iphone)
        assertEquals(200, profile.status)
        assertEquals("application/x-apple-aspen-config", profile.contentType)
        assertTrue(profile.extraHeaders["Content-Disposition"]!!.contains("adshield.mobileconfig"))
    }

    @Test fun rejectsUnknownPathsAndMethods() {
        assertEquals(404, ShareContent.route("GET", "/etc/passwd", null).status)
        assertEquals(404, ShareContent.route("GET", "/../secret", null).status)
        assertEquals(405, ShareContent.route("POST", "/", null).status)
        assertEquals(405, ShareContent.route("DELETE", ShareContent.PROFILE_PATH, null).status)
        assertEquals(200, ShareContent.route("HEAD", "/", null).status)
    }

    @Test fun mobileConfigIsWellFormedAndConfiguresEncryptedDns() {
        val factory = DocumentBuilderFactory.newInstance().apply {
            // Don't fetch Apple's DTD over the network during tests.
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val doc = factory.newDocumentBuilder().parse(ShareContent.mobileConfig().byteInputStream())
        assertEquals("plist", doc.documentElement.tagName)

        val strings = doc.getElementsByTagName("string").let { nl -> (0 until nl.length).map { nl.item(it).textContent } }
        assertTrue("com.apple.dnsSettings.managed" in strings)
        assertTrue("Configuration" in strings)
        assertTrue("HTTPS" in strings)
        assertTrue(ShareContent.DOH_URL in strings)
        ShareContent.DNS4.forEach { assertTrue(it in strings) }
        ShareContent.DNS6.forEach { assertTrue(it in strings) }

        // Each PayloadUUID must be a distinct UUID.
        val keys = doc.getElementsByTagName("key")
        val uuids = (0 until keys.length).map { keys.item(it) as Element }
            .filter { it.textContent == "PayloadUUID" }
            .map { var n = it.nextSibling; while (n !is Element) n = n.nextSibling; n.textContent }
        assertEquals(2, uuids.size)
        assertEquals(2, uuids.toSet().size)
        uuids.forEach { java.util.UUID.fromString(it) }
    }

    @Test fun dohUrlUsesHttps() {
        assertTrue(ShareContent.DOH_URL.startsWith("https://"))
        assertTrue(ShareContent.DOH_URL.contains(ShareContent.DOT_HOST))
    }

    @Test fun indexPutsVisitorsPlatformFirst() {
        fun firstSection(p: Platform) = Regex("<section id=\"(\\w+)\"").find(ShareContent.indexHtml(p))!!.groupValues[1]
        assertEquals("apple", firstSection(Platform.IOS))
        assertEquals("apple", firstSection(Platform.MAC))
        assertEquals("android", firstSection(Platform.ANDROID))
        assertEquals("windows", firstSection(Platform.WINDOWS))
        assertEquals("apple", firstSection(Platform.OTHER))
    }

    @Test fun indexContainsEverySectionAndTheSettings() {
        Platform.entries.forEach { p ->
            val html = ShareContent.indexHtml(p)
            listOf("apple", "android", "windows", "other").forEach { assertTrue(html.contains("id=\"$it\"")) }
            assertTrue(html.contains(ShareContent.PROFILE_PATH.removePrefix("/")))
            assertTrue(html.contains(ShareContent.DOT_HOST))
            assertTrue(html.contains(ShareContent.DNS4[0]))
            assertTrue(html.contains("name=\"viewport\""))
            assertTrue(html.contains("prefers-color-scheme:dark"))
        }
    }
}

class ShareServerTest {
    private val server = ShareServer(preferredPorts = 18080..18090)

    @After fun tearDown() = server.stop()

    private fun get(path: String, method: String = "GET", ua: String? = null): Triple<Int, String?, String> {
        val c = URL("http://127.0.0.1:${server.port}/${server.token}$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        ua?.let { c.setRequestProperty("User-Agent", it) }
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.readBytes()?.decodeToString().orEmpty()
        return Triple(code, c.contentType, body).also { c.disconnect() }
    }

    @Test fun servesIndexAndProfileOverHttp() {
        server.start()
        assertTrue(server.isRunning)
        val (code, type, body) = get("/", ua = "iPhone")
        assertEquals(200, code)
        assertTrue(type!!.startsWith("text/html"))
        assertTrue(body.contains("Block ads on this device"))

        val (pcode, ptype, pbody) = get(ShareContent.PROFILE_PATH)
        assertEquals(200, pcode)
        assertEquals("application/x-apple-aspen-config", ptype)
        assertEquals(ShareContent.mobileConfig(), pbody)
    }

    @Test fun returns404And405() {
        server.start()
        assertEquals(404, get("/nope").first)
        assertEquals(405, get("/", method = "POST").first)
    }

    @Test fun headHasNoBody() {
        server.start()
        val (code, _, body) = get("/", method = "HEAD")
        assertEquals(200, code)
        assertEquals("", body)
    }

    @Test fun pathsWithoutTheSecretTokenAre404() {
        server.start()
        fun raw(path: String): Int {
            val c = URL("http://127.0.0.1:${server.port}$path").openConnection() as HttpURLConnection
            return c.responseCode.also { c.disconnect() }
        }
        assertEquals(404, raw("/"))
        assertEquals(404, raw(ShareContent.PROFILE_PATH))
        assertEquals(404, raw("/0000000000000000000000000000000/"))
        assertEquals(404, raw("/${server.token}"))           // must include the trailing slash
        assertEquals(200, raw("/${server.token}/"))
        assertEquals(200, raw("/${server.token}${ShareContent.PROFILE_PATH}"))
    }

    @Test fun tokenIsRandomAndChangesOnRestart() {
        server.start()
        val first = server.token
        assertEquals(32, first.length)
        assertTrue(first.matches(Regex("[0-9a-f]{32}")))
        server.stop(); server.start(java.net.InetAddress.getByName("127.0.0.1"))
        assertNotEquals(first, server.token)
    }

    @Test fun linkExpires() {
        server.lifetimeMs = 200
        server.start()
        assertEquals(200, get("/").first)
        Thread.sleep(400)
        assertTrue(server.isExpired)
        assertEquals(404, get("/").first)
        server.clearExpiry()
        assertFalse(server.isExpired)
    }

    @Test fun startIsIdempotent() {
        val a = server.start()
        val b = server.start()
        assertEquals(a, b)
    }

    @Test fun fallsBackWhenPreferredPortIsTaken() {
        ServerSocket(18100, 16, java.net.InetAddress.getLoopbackAddress()).use { busy ->
            val s = ShareServer(preferredPorts = busy.localPort..busy.localPort)
            try {
                val port = s.start()
                assertNotEquals(busy.localPort, port)
                assertTrue(port > 0)
            } finally { s.stop() }
        }
    }

    @Test fun stopReleasesPortAndCanRestart() {
        server.start()
        server.stop()
        assertFalse(server.isRunning)
        assertEquals(-1, server.port)
        val p = server.start()
        assertTrue(p > 0)
        assertEquals(200, get("/").first)
    }

    @Test fun handlesManyConcurrentClients() {
        server.start()
        val codes = java.util.concurrent.ConcurrentLinkedQueue<Int>()
        val threads = (1..20).map { Thread { codes += get("/").first } }
        threads.forEach { it.start() }
        threads.forEach { it.join(10_000) }
        assertEquals(20, codes.size)
        assertTrue(codes.all { it == 200 })
    }
}

class LanAddressTest {
    @Test fun prefersHotspotInterface() {
        val ip = LanAddress.pick(listOf("wlan0" to "192.168.1.20", "swlan0" to "192.168.43.1", "rmnet0" to "10.1.2.3"))
        assertEquals("192.168.43.1", ip)
        assertEquals("10.42.0.1", LanAddress.pick(listOf("wlan0" to "192.168.1.5", "ap0" to "10.42.0.1")))
        assertEquals("192.168.50.1", LanAddress.pick(listOf("wlan0" to "192.168.1.5", "wlan1" to "192.168.50.1")))
    }

    @Test fun fallsBackToWifi() {
        assertEquals("192.168.1.20", LanAddress.pick(listOf("rmnet_data0" to "10.5.5.5", "wlan0" to "192.168.1.20")))
    }

    @Test fun skipsVpnMobileAndPublicAddresses() {
        assertNull(LanAddress.pick(listOf(
            "tun0" to "10.111.222.2", "rmnet0" to "10.0.0.5", "ccmni0" to "10.0.0.6",
            "wlan0" to "8.8.8.8", "lo" to "127.0.0.1", "dummy0" to "192.168.9.9"
        )))
        // Our own tunnel address is skipped even if the interface is oddly named.
        assertNull(LanAddress.pick(listOf("eth9" to "10.111.222.2")))
        assertNull(LanAddress.pick(emptyList()))
    }

    @Test fun privateRanges() {
        listOf("10.0.0.1", "172.16.5.4", "172.31.255.1", "192.168.0.1").forEach { assertTrue(it, LanAddress.isPrivate(it)) }
        listOf("8.8.8.8", "172.32.0.1", "100.64.0.1", "fe80::1", "not-an-ip").forEach { assertFalse(it, LanAddress.isPrivate(it)) }
    }

    @Test fun urlFormat() {
        assertEquals("http://192.168.43.1:8080/abc/", LanAddress.url("192.168.43.1", 8080, "abc"))
    }

    @Test fun candidatesNeverThrow() {
        LanAddress.candidates().forEach { (name, ip) -> assertTrue(name.isNotEmpty() && ip.contains('.')) }
    }
}

class QrTest {
    private fun decode(text: String): String {
        val m = qrMatrix(text)
        val scale = 4; val margin = 4 * scale
        val w = m.width * scale + 2 * margin
        val pixels = IntArray(w * w) { 0xFFFFFF }
        for (y in 0 until m.height) for (x in 0 until m.width) if (m[x, y]) {
            for (dy in 0 until scale) for (dx in 0 until scale)
                pixels[(margin + y * scale + dy) * w + margin + x * scale + dx] = 0
        }
        val bmp = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, w, pixels)))
        return QRCodeReader().decode(bmp).text
    }

    @Test fun qrRoundTripsTheShareUrl() {
        val url = "http://192.168.43.1:8080/"
        assertEquals(url, decode(url))
    }

    @Test fun qrHandlesLongUrls() {
        val url = "http://192.168.100.200:65535/" + "x".repeat(200)
        assertEquals(url, decode(url))
    }

    @Test fun qrIsSquareWithNoQuietZone() {
        val m = qrMatrix("http://10.0.0.1:8080/")
        assertEquals(m.width, m.height)
        assertTrue(m[0, 0]) // finder pattern starts at the very corner
    }
}
