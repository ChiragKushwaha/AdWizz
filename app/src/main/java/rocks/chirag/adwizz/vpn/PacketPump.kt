package rocks.chirag.adwizz.vpn

import android.os.ParcelFileDescriptor
import android.util.Log
import rocks.chirag.adwizz.BuildConfig
import rocks.chirag.adwizz.core.DnsMessage
import rocks.chirag.adwizz.core.DnsResponse
import rocks.chirag.adwizz.core.DomainMatcher
import rocks.chirag.adwizz.core.IpBuilder
import rocks.chirag.adwizz.core.IpPacket
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
        val response = respond(pkt.payload) ?: return
        write(IpBuilder.udp(pkt.dstAddr, pkt.srcAddr, 53, pkt.srcPort, response))
    }

    private fun respond(payload: ByteArray): ByteArray? {
        val query = DnsMessage.parseQuery(payload) ?: return upstream.forward(payload)
        val m = matcher()
        if (query.qname in DOH_CANARIES) return DnsMessage.nxDomain(payload, query)
        if (m.isBlocked(query.qname)) return decided(query.qname, true) { DnsMessage.blockedResponse(payload, query) }
        val answer = upstream.forward(payload)
        // CNAME cloaking: "metrics.news-site.com" may just be an alias for an ad/tracker domain.
        val cloaked = answer != null && !m.isAllowed(query.qname) && DnsResponse.cnameTargets(answer).any { m.isBlocked(it) }
        return decided(query.qname, cloaked) {
            when {
                cloaked -> DnsMessage.blockedResponse(payload, query)
                answer != null -> answer
                else -> DnsMessage.servFail(payload, query)
            }
        }
    }

    /** Records the decision for the Activity tab (and the debug log used by the device test). */
    private inline fun decided(name: String, blocked: Boolean, response: () -> ByteArray): ByteArray {
        VpnState.record(name, blocked)
        if (BuildConfig.DEBUG) Log.d(DNS_LOG_TAG, (if (blocked) "BLOCK " else "ALLOW ") + name)
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
