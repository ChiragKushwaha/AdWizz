package rocks.chirag.adshield.share

import java.net.InetAddress
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
        private const val BACKLOG = 50
        private val random = java.security.SecureRandom()
        fun newToken(): String = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }
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
            // Bounded reads: a client can't make us buffer an endless line or header block.
            val req = HttpIo.readRequest(c.getInputStream()) ?: return@runCatching
            HttpIo.writeResponse(c.getOutputStream(), routeWithToken(req.method, req.path, req.userAgent), req.method != "HEAD")
        }
    }

    private fun routeWithToken(method: String, path: String, userAgent: String?): ShareContent.Response {
        val prefix = "/$token/"
        if (isExpired || !path.startsWith(prefix)) {
            return ShareContent.Response(404, "text/plain; charset=utf-8", "Not found".toByteArray())
        }
        return ShareContent.route(method, "/" + path.removePrefix(prefix), userAgent)
    }
}
