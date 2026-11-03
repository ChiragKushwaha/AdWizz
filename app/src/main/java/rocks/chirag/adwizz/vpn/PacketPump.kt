package rocks.chirag.adwizz.vpn

import android.os.ParcelFileDescriptor
import android.util.Log
import rocks.chirag.adwizz.BuildConfig
import rocks.chirag.adwizz.core.DnsMessage
import rocks.chirag.adwizz.core.DnsResponse
import rocks.chirag.adwizz.core.DomainMatcher
import rocks.chirag.adwizz.core.IpBuilder
import rocks.chirag.adwizz.core.IpPacket
import rocks.chirag.adwizz.data.BlockCategory
import rocks.chirag.adwizz.data.BlockStats
import rocks.chirag.adwizz.data.VpnState
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Reads packets from the tunnel and answers them: blocked domains get an instant 0.0.0.0 / ::,
 * everything else goes to [upstream]. Packets come from any app on the phone, so a malformed one
 * is dropped — it must never take the VPN down.
 */
internal class PacketPump(
    private val tun: ParcelFileDescriptor,
    private val matcher: () -> DomainMatcher,
    private val upstream: UpstreamDns,
    /** Which app sent a query (null if Android can't tell us). */
    private val appOf: (IpPacket) -> String? = { null },
    /** Apps whose internet is switched off. */
    private val firewalled: () -> Set<String> = { emptySet() },
) {
    private val out = FileOutputStream(tun.fileDescriptor)
    private val workers = ThreadPoolExecutor(4, 32, 30, TimeUnit.SECONDS, ArrayBlockingQueue(1024), ThreadPoolExecutor.DiscardOldestPolicy())
    private val reader = Thread(::readLoop, "adwizz-tun")

    fun start() = reader.start()

    fun stop() {
        reader.interrupt()
        workers.shutdownNow()
    }

    private fun readLoop() {
        val input = FileInputStream(tun.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val n = input.read(buf)
                if (n > 0) runCatchingMalformed("packet") { route(buf, n) }
            }
        } catch (e: IOException) {
            // Interface closed: normal on shutdown.
        }
    }

    private fun route(buf: ByteArray, n: Int) {
        val pkt = IpPacket.parse(buf, n) ?: return
        when {
            pkt.protocol == IpPacket.PROTO_UDP && pkt.dstPort == 53 -> workers.execute { runCatchingMalformed("DNS query") { answer(pkt) } }
            // Never answer ICMP with ICMP (avoids loops with error messages).
            pkt.protocol == IpPacket.PROTO_ICMP || pkt.protocol == IpPacket.PROTO_ICMPV6 -> Unit
            // e.g. DNS-over-TLS / DoH to a hard-coded resolver: refuse quickly so the app falls
            // back to plain DNS, which we can filter.
            else -> write(IpBuilder.icmpUnreachable(buf.copyOf(n), n))
        }
    }

    private fun answer(pkt: IpPacket) {
        val response = respond(pkt) ?: return
        write(IpBuilder.udp(pkt.dstAddr, pkt.srcAddr, 53, pkt.srcPort, response))
    }

    private fun respond(pkt: IpPacket): ByteArray? {
        val payload = pkt.payload
        val query = DnsMessage.parseQuery(payload) ?: return upstream.forward(payload)
        if (query.qname in DOH_CANARIES) return DnsMessage.nxDomain(payload, query)
        // Paused: everything goes through unfiltered (and isn't counted).
        if (VpnState.isPaused()) return upstream.forward(payload) ?: DnsMessage.servFail(payload, query)
        // Ask before forwarding: the app's socket is still open while it waits for this answer.
        val app = appOf(pkt)
        if (app != null && app in firewalled()) {
            return decided(query.qname, app, BlockCategory.FIREWALL) { DnsMessage.blockedResponse(payload, query) }
        }
        val m = matcher()
        BlockCategory.forLookup(m.blockedBy(query.qname))?.let { category ->
            return decided(query.qname, app, category) { DnsMessage.blockedResponse(payload, query) }
        }
        val answer = upstream.forward(payload)
        // CNAME cloaking: "metrics.news-site.com" may just be an alias for an ad/tracker domain.
        val cloakedBy = if (answer == null || m.isAllowed(query.qname)) null
        else DnsResponse.cnameTargets(answer).firstNotNullOfOrNull { BlockCategory.forLookup(m.blockedBy(it)) }
        return decided(query.qname, app, cloakedBy) {
            when {
                cloakedBy != null -> DnsMessage.blockedResponse(payload, query)
                answer != null -> answer
                else -> DnsMessage.servFail(payload, query)
            }
        }
    }

    /** Records the decision for Recent activity, the Stats tab and the debug log used by the device test. */
    private inline fun decided(name: String, app: String?, category: BlockCategory?, response: () -> ByteArray): ByteArray {
        val blocked = category != null
        VpnState.record(name, blocked, app, category)
        BlockStats.record(app, name, category)
        if (BuildConfig.DEBUG) Log.d(DNS_LOG_TAG, (if (blocked) "BLOCK " else "ALLOW ") + name + " " + (app ?: ""))
        return response()
    }

    private fun write(packet: ByteArray) = synchronized(out) {
        try { out.write(packet) } catch (e: IOException) { }
    }

    private inline fun runCatchingMalformed(what: String, block: () -> Unit) {
        try { block() } catch (e: RuntimeException) { Log.w(TAG, "Dropped malformed $what", e) }
    }

    companion object {
        private const val TAG = "AdWizzVpn"
        const val DNS_LOG_TAG = "AdWizzDns"

        /** Asking for this name tells Firefox not to switch on its own DNS-over-HTTPS. */
        private val DOH_CANARIES = setOf("use-application-dns.net")
    }
}
