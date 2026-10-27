package rocks.chirag.adshield.share

import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element
import rocks.chirag.adshield.share.ShareContent.Platform

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
