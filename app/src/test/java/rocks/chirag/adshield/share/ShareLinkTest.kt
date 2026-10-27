package rocks.chirag.adshield.share

import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** The secret, expiring setup link. */
class ShareLinkTest {
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
}
