package rocks.chirag.adshield.share

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors

/**
 * Minimal HTTP server that serves [ShareContent] to devices on the hotspot / same Wi-Fi.
 * Only runs while the Devices screen is open. Plain Java networking, so it's JVM-testable.
 */
class ShareServer(private val preferredPorts: IntRange = 8080..8090) {
    companion object {
        const val MAX_LINE = 4096
        const val MAX_HEADERS = 64

        private val random = java.security.SecureRandom()
        fun newToken(): String = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        private const val BACKLOG = 50

        /** Reads one CRLF/LF-terminated line of at most [MAX_LINE] bytes; null at EOF or if too long. */
        fun readLineLimited(input: java.io.InputStream): String? {
            val sb = StringBuilder()
            while (true) {
                val b = input.read()
                if (b < 0) return if (sb.isEmpty()) null else sb.toString()
                if (b == '\n'.code) return sb.toString().trimEnd('\r')
                if (sb.length >= MAX_LINE) return null
                sb.append(b.toChar())
            }
        }
    }

    @Volatile private var socket: ServerSocket? = null
    private var pool = Executors.newFixedThreadPool(4)

    val port: Int get() = socket?.localPort ?: -1

    /**
     * Secret part of the URL (128 random bits, new on every start). Only the QR code carries it,
     * so other devices on the network can't find or probe the page; every other path is a 404.
     */
    @Volatile var token: String = newToken()
        private set

    /** The page stops being served this long after it was started. */
    var lifetimeMs: Long = 15 * 60 * 1000L
    @Volatile private var expiresAt = 0L
    val isExpired: Boolean get() = expiresAt != 0L && System.currentTimeMillis() > expiresAt

    /** Lets the next [start] issue a fresh link after the old one expired. */
    fun clearExpiry() { expiresAt = 0L }
    val isRunning: Boolean get() = socket?.isClosed == false

    /** Address the server is listening on, or null when stopped. */
    val boundAddress: InetAddress? get() = socket?.takeIf { !it.isClosed }?.inetAddress

    /**
     * Starts on the first free port in [preferredPorts] (or any free port), listening ONLY on
     * [bind] — the hotspot / Wi-Fi address — never on every interface, so the page isn't reachable
     * from mobile data (which often has a public IPv6 address). Defaults to loopback.
     * Restarts if it was listening on a different address. Returns the port.
     */
    @Synchronized
    fun start(bind: InetAddress = InetAddress.getLoopbackAddress()): Int {
        socket?.let { if (!it.isClosed && it.inetAddress == bind) return it.localPort }
        stop()
        token = newToken()
        expiresAt = System.currentTimeMillis() + lifetimeMs
        pool = Executors.newFixedThreadPool(4)
        val s = preferredPorts.firstNotNullOfOrNull { p -> runCatching { ServerSocket(p, BACKLOG, bind) }.getOrNull() }
            ?: ServerSocket(0, BACKLOG, bind)
        socket = s
        Thread({ acceptLoop(s) }, "adshield-share").apply { isDaemon = true }.start()
        return s.localPort
    }

    @Synchronized
    fun stop() {
        runCatching { socket?.close() }
        socket = null
        pool.shutdownNow()
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val client = try { s.accept() } catch (e: SocketException) { return }
            runCatching { pool.execute { handle(client) } }.onFailure { runCatching { client.close() } }
        }
    }

    private fun handle(client: Socket) = client.use { c ->
        runCatching {
            c.soTimeout = 5000
            val input = c.getInputStream()
            // Bounded reads: a client can't make us buffer an endless line or header block.
            val requestLine = readLineLimited(input) ?: return@runCatching
            var userAgent: String? = null
            for (i in 0 until MAX_HEADERS) {
                val line = readLineLimited(input) ?: break
                if (line.isEmpty()) break
                if (line.startsWith("User-Agent:", ignoreCase = true)) userAgent = line.substringAfter(':').trim().take(512)
            }
            val parts = requestLine.split(' ')
            val method = parts.getOrElse(0) { "" }
            val path = parts.getOrElse(1) { "/" }
            val r = routeWithToken(method, path, userAgent)
            val head = buildString {
                append("HTTP/1.1 ${r.status} ${reason(r.status)}\r\n")
                append("Content-Type: ${r.contentType}\r\n")
                append("Content-Length: ${r.body.size}\r\n")
                append("Cache-Control: no-store\r\n")
                append("Connection: close\r\n")
                r.extraHeaders.forEach { (k, v) -> append("$k: $v\r\n") }
                append("\r\n")
            }
            val out = c.getOutputStream()
            out.write(head.toByteArray(Charsets.ISO_8859_1))
            if (method != "HEAD") out.write(r.body)
            out.flush()
        }
    }

    private fun routeWithToken(method: String, path: String, userAgent: String?): ShareContent.Response {
        val prefix = "/$token/"
        if (isExpired || !path.startsWith(prefix)) {
            return ShareContent.Response(404, "text/plain; charset=utf-8", "Not found".toByteArray())
        }
        return ShareContent.route(method, "/" + path.removePrefix(prefix), userAgent)
    }

    private fun reason(code: Int) = when (code) {
        200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; else -> "Error"
    }
}

/** Finds the address other devices can reach this phone on. */
object LanAddress {
    /** (interface name, IPv4 address) of every up, non-loopback interface. */
    fun candidates(): List<Pair<String, String>> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { ni ->
                ni.inetAddresses.toList().filterIsInstance<Inet4Address>().map { ni.name to it.hostAddress!! }
            }
    }.getOrDefault(emptyList())

    /**
     * Picks the best address: the hotspot interface first (ap / swlan / softap / wlan1), then
     * Wi-Fi. Mobile data and VPN interfaces are skipped since other devices can't reach them.
     */
    fun pick(candidates: List<Pair<String, String>>): String? {
        val usable = candidates.filter { (name, ip) ->
            val n = name.lowercase()
            !n.startsWith("tun") && !n.startsWith("rmnet") && !n.startsWith("ccmni") &&
                !n.startsWith("dummy") && !n.startsWith("lo") && isPrivate(ip) &&
                ip != "10.111.222.2" // AdShield's own tunnel address
        }
        fun rank(name: String): Int {
            val n = name.lowercase()
            return when {
                n.startsWith("ap") || n.startsWith("swlan") || n.startsWith("softap") -> 0
                n == "wlan1" || n.startsWith("wlan1") -> 1
                n.startsWith("wlan") -> 2
                n.startsWith("eth") || n.startsWith("rndis") || n.startsWith("usb") -> 3
                else -> 4
            }
        }
        return usable.minByOrNull { rank(it.first) }?.second
    }

    fun isPrivate(ip: String): Boolean = runCatching {
        val a = InetAddress.getByName(ip)
        a is Inet4Address && a.isSiteLocalAddress
    }.getOrDefault(false)

    fun url(ip: String, port: Int, token: String) = "http://$ip:$port/$token/"
}
