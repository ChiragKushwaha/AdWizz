package rocks.chirag.adshield.vpn

import android.net.ConnectivityManager
import android.util.Log
import rocks.chirag.adshield.core.DnsResponse
import rocks.chirag.adshield.data.Settings
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException

/**
 * Forwards allowed queries to the chosen resolvers, then to the current network's own DNS (covers
 * Wi-Fi that blocks outside DNS, captive portals and carriers without IPv4). Starts from whichever
 * server answered last.
 *
 * @param protect excludes a socket from the VPN (VpnService.protect) so it can't loop back in.
 */
internal class UpstreamDns(private val protect: (DatagramSocket) -> Boolean) {
    @Volatile private var configured: List<InetAddress> = emptyList()
    @Volatile private var networkDns: List<InetAddress> = emptyList()
    @Volatile private var preferred = 0

    fun configure(settings: Settings) {
        configured = listOf(settings.primaryDns, settings.secondaryDns)
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { InetAddress.getByName(it.trim()) }.getOrNull() }
    }

    /** DNS servers of the real (non-VPN) network: this app is excluded from its own VPN. */
    fun refreshNetworkDns(cm: ConnectivityManager?) {
        val lp = runCatching { cm?.getLinkProperties(cm.activeNetwork) }.getOrNull()
        networkDns = lp?.dnsServers.orEmpty().filter { !it.isLoopbackAddress && !TunnelAddresses.isOurs(it.hostAddress ?: "") }
        preferred = 0
    }

    override fun toString() = "upstreams=$configured + network=$networkDns"

    fun forward(query: ByteArray): ByteArray? {
        val servers = (configured + networkDns).distinct().ifEmpty { listOf(InetAddress.getByName("1.1.1.1")) }
        val start = preferred.coerceIn(0, servers.size - 1)
        for (k in servers.indices) {
            val i = (start + k) % servers.size
            val answer = ask(servers[i], query) ?: continue
            preferred = i
            return answer
        }
        return null
    }

    private fun ask(server: InetAddress, query: ByteArray): ByteArray? = try {
        DatagramSocket().use { sock ->
            protect(sock)
            sock.soTimeout = 2500
            // connect(): the OS drops datagrams from anyone but this server (no spoofed answers).
            sock.connect(server, 53)
            sock.send(DatagramPacket(query, query.size))
            val buf = ByteArray(4096)
            while (true) {
                val resp = DatagramPacket(buf, buf.size)
                sock.receive(resp)
                val r = buf.copyOf(resp.length)
                // Only accept the answer to *this* query (same ID, marked as a response).
                if (DnsResponse.isResponseTo(query, r)) return@use r
            }
            @Suppress("UNREACHABLE_CODE") null
        }
    } catch (e: SocketTimeoutException) {
        Log.d(TAG, "Upstream $server timed out"); null
    } catch (e: IOException) {
        Log.d(TAG, "Upstream $server failed: ${e.message}"); null
    }

    private companion object { const val TAG = "AdWizzDns" }
}
