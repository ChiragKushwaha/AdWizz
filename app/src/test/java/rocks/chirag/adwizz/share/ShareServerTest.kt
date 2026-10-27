package rocks.chirag.adwizz.share

import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

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
