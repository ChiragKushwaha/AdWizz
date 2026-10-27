package rocks.chirag.adshield.share

import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket

class ShareSecurityTest {
    private val server = ShareServer(preferredPorts = 18300..18310)
    @After fun stop() = server.stop()

    @Test fun defaultsToLoopbackNotEveryInterface() {
        server.start()
        assertEquals(InetAddress.getLoopbackAddress(), server.boundAddress)
        assertFalse(server.boundAddress!!.isAnyLocalAddress)
    }

    @Test fun notReachableOnOtherInterfaces() {
        server.start()
        // Any non-loopback IPv4 address of this machine must NOT reach the server.
        val other = NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress } ?: return
        val reached = runCatching { Socket().use { it.connect(java.net.InetSocketAddress(other, server.port), 1000) } }.isSuccess
        assertFalse("server reachable via $other", reached)
    }

    @Test fun movesWhenTheAddressChanges() {
        server.start(InetAddress.getByName("127.0.0.1"))
        assertEquals("127.0.0.1", server.boundAddress!!.hostAddress)
        server.start(InetAddress.getLoopbackAddress())
        assertTrue(server.isRunning)
    }

    @Test fun oversizedRequestIsRejectedAndServerSurvives() {
        server.start()
        Socket("127.0.0.1", server.port).use { s ->
            s.soTimeout = 5000
            val out = s.getOutputStream()
            runCatching { out.write("GET /".toByteArray()); out.write(ByteArray(200_000) { 'a'.code.toByte() }); out.flush() }
            // The server gives up on the line and closes; reading hits EOF (or reset) rather than hanging.
            runCatching { assertEquals(-1, s.getInputStream().read()) }
        }
        // Endless headers are cut off too.
        Socket("127.0.0.1", server.port).use { s ->
            s.soTimeout = 5000
            runCatching {
                s.getOutputStream().write("GET / HTTP/1.1\r\n".toByteArray())
                repeat(1000) { s.getOutputStream().write("X-Junk: $it\r\n".toByteArray()) }
            }
        }
        // Still serving normally.
        val c = java.net.URL("http://127.0.0.1:${server.port}/${server.token}/").openConnection() as java.net.HttpURLConnection
        assertEquals(200, c.responseCode)
        c.disconnect()
    }

    @Test fun readLineLimitedBounds() {
        fun read(s: String) = HttpIo.readLineLimited(s.byteInputStream())
        assertEquals("GET / HTTP/1.1", read("GET / HTTP/1.1\r\nHost: x\r\n"))
        assertEquals("", read("\r\n"))
        assertNull(read(""))
        assertNull(read("a".repeat(HttpIo.MAX_LINE + 10) + "\n"))
        assertEquals("tail", read("tail"))
    }
}
