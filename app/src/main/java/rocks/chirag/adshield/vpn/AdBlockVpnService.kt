package rocks.chirag.adshield.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import rocks.chirag.adshield.BuildConfig
import rocks.chirag.adshield.R
import rocks.chirag.adshield.core.DnsMessage
import rocks.chirag.adshield.core.DomainMatcher
import rocks.chirag.adshield.core.IpPacket
import rocks.chirag.adshield.data.BlocklistRepository
import rocks.chirag.adshield.data.Settings
import rocks.chirag.adshield.data.Status
import rocks.chirag.adshield.data.VpnState
import rocks.chirag.adshield.ui.MainActivity
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * A local, on-device VPN that only carries DNS.
 *
 * The tunnel advertises fake DNS servers (10.111.222.1 and fdad:5e1d::1) to every app. Only those
 * addresses (plus well-known public resolvers, optionally) are routed into the tunnel, so all other
 * traffic keeps flowing over the normal network at full speed — nothing leaves the phone.
 *
 * Each DNS query is checked against the blocklist: ad/tracker domains get an instant 0.0.0.0 / ::
 * answer; everything else is forwarded to the chosen resolver through a protected socket, falling
 * back to the current Wi-Fi/mobile network's own DNS if the chosen resolver is unreachable.
 */
class AdBlockVpnService : VpnService() {

    private lateinit var settings: Settings
    private lateinit var repo: BlocklistRepository

    @Volatile private var matcher: DomainMatcher = DomainMatcher(emptySet(), emptySet())
    @Volatile private var configuredUpstreams: List<InetAddress> = emptyList()
    @Volatile private var networkDns: List<InetAddress> = emptyList()
    /** Index into the combined upstream list of the last server that answered; tried first next time. */
    @Volatile private var preferred = 0

    private var tun: ParcelFileDescriptor? = null
    private var out: FileOutputStream? = null
    private var readerThread: Thread? = null
    private var workers: ThreadPoolExecutor? = null
    private var refresher: ScheduledExecutorService? = null
    @Volatile private var starting = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        repo = BlocklistRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                settings.wantEnabled = false
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> {
                if (tun != null) Thread {
                    reloadConfig()
                    refreshListsIfNeeded() // a list switched on in Settings may not be downloaded yet
                }.start()
                return START_STICKY
            }
            ACTION_RESTART -> {
                if (tun == null && !starting) return START_STICKY
                shutdown(stopService = false)
                startTunnel()
                return START_STICKY
            }
            // ACTION_START, or a null intent when the system restarts us / Always-on VPN kicks in.
            else -> {
                settings.wantEnabled = true
                // Must be called promptly after startForegroundService(), even if already running.
                goForeground()
                if (tun == null && !starting) startTunnel()
                return START_STICKY
            }
        }
    }

    private fun startTunnel() {
        starting = true
        VpnState.setStatus(Status.STARTING)
        goForeground()
        Thread {
            try {
                reloadConfig()
                establish()
                watchNetwork()
                starting = false
                // Download missing lists now, and keep them fresh while running (hot-swaps the matcher).
                refresher = Executors.newSingleThreadScheduledExecutor().also {
                    it.scheduleWithFixedDelay({ refreshListsIfNeeded() }, 0, 1, TimeUnit.HOURS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start", e)
                starting = false
                VpnState.setStatus(Status.ERROR, e.message ?: "Failed to start VPN")
                shutdown()
            }
        }.start()
    }

    private fun refreshListsIfNeeded() {
        try {
            if (tun != null && repo.needsDownload()) {
                Log.i(TAG, repo.downloadAll())
                reloadConfig()
            }
        } catch (e: Exception) {
            Log.w(TAG, "List refresh failed", e)
        }
    }

    private fun reloadConfig() {
        matcher = repo.buildMatcher()
        configuredUpstreams = listOf(settings.primaryDns, settings.secondaryDns)
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { InetAddress.getByName(it.trim()) }.getOrNull() }
        refreshNetworkDns()
        Log.i(TAG, "Loaded ${matcher.size()} blocked domains, upstreams=$configuredUpstreams + network=$networkDns")
    }

    /**
     * DNS servers of the real (non-VPN) network. Because this app is excluded from its own VPN,
     * the active network seen here is the underlying Wi-Fi/mobile network.
     */
    private fun refreshNetworkDns() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val lp: LinkProperties? = runCatching { cm.getLinkProperties(cm.activeNetwork) }.getOrNull()
        networkDns = lp?.dnsServers.orEmpty().filter { addr ->
            val s = addr.hostAddress ?: ""
            !addr.isLoopbackAddress && s != FAKE_DNS4 && !s.startsWith("fdad:5e1d")
        }
        preferred = 0
    }

    private fun watchNetwork() {
        if (Build.VERSION.SDK_INT < 24 || networkCallback != null) return
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refreshNetworkDns()
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = refreshNetworkDns()
        }
        runCatching { cm.registerDefaultNetworkCallback(cb) }.onSuccess { networkCallback = cb }
    }

    private fun establish() {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(1500)
            .addAddress(TUN_ADDRESS4, 32)
            .addDnsServer(FAKE_DNS4)
            .addRoute(FAKE_DNS4, 32)
            .setBlocking(true)
            .setConfigureIntent(mainActivityIntent())

        // IPv6 side: some devices/networks (e.g. IPv6-first mobile carriers) query DNS over IPv6.
        // Kernels without IPv6 throw here; IPv4-only filtering still works in that case.
        val v6 = runCatching {
            builder.addAddress(TUN_ADDRESS6, 128)
            builder.addDnsServer(FAKE_DNS6)
            builder.addRoute(FAKE_DNS6, 128)
        }.isSuccess

        if (settings.catchHardcodedDns) {
            HARDCODED_RESOLVERS4.forEach { addRoute(builder, it, 32) }
            if (v6) HARDCODED_RESOLVERS6.forEach { addRoute(builder, it, 128) }
        }
        // Our own traffic (list downloads, upstream DNS) must never loop back into the tunnel.
        runCatching { builder.addDisallowedApplication(packageName) }
        // Apps the user excluded (e.g. banking/streaming apps that refuse to run with a VPN) use the
        // normal network directly, so the VPN isn't their active network. They aren't filtered.
        settings.excludedApps.forEach { pkg ->
            runCatching { builder.addDisallowedApplication(pkg) } // throws if the app was uninstalled
        }
        if (Build.VERSION.SDK_INT >= 29) builder.setMetered(false)

        val pfd = builder.establish() ?: throw IllegalStateException("VPN permission not granted")
        tun = pfd
        out = FileOutputStream(pfd.fileDescriptor)
        workers = ThreadPoolExecutor(
            4, 32, 30, TimeUnit.SECONDS, ArrayBlockingQueue(1024),
            ThreadPoolExecutor.DiscardOldestPolicy()
        )
        readerThread = Thread({ readLoop(pfd) }, "adshield-tun").also { it.start() }
        VpnState.resetSession()
        VpnState.setStatus(Status.RUNNING)
        TileUpdater.request(this)
    }

    /** Adds "ip" or "ip/prefix" as a route; bad entries are skipped. */
    private fun addRoute(builder: Builder, entry: String, defaultPrefix: Int) {
        val ip = entry.substringBefore('/')
        val prefix = entry.substringAfter('/', "").toIntOrNull() ?: defaultPrefix
        runCatching { builder.addRoute(ip, prefix) }
    }

    private fun readLoop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (!Thread.currentThread().isInterrupted) {
                val n = input.read(buf)
                if (n <= 0) continue
                // Packets come from any app on the phone: a malformed one must never take the VPN down.
                try {
                    val pkt = IpPacket.parse(buf, n) ?: continue
                    when {
                        pkt.protocol == IpPacket.PROTO_UDP && pkt.dstPort == 53 ->
                            workers?.execute { safeHandleDns(pkt) }
                        // Never answer ICMP with ICMP (avoids loops with error messages).
                        pkt.protocol == IpPacket.PROTO_ICMP || pkt.protocol == IpPacket.PROTO_ICMPV6 -> Unit
                        // e.g. DNS-over-TLS / DoH to a hard-coded resolver: refuse quickly so the app
                        // falls back to plain DNS, which we can filter.
                        else -> write(IpPacket.buildIcmpUnreachable(buf.copyOf(n), n))
                    }
                } catch (e: RuntimeException) {
                    Log.w(TAG, "Dropped malformed packet", e)
                }
            }
        } catch (e: IOException) {
            // Interface closed — normal on shutdown.
        }
    }

    private fun safeHandleDns(pkt: IpPacket) {
        try { handleDns(pkt) } catch (e: RuntimeException) { Log.w(TAG, "Dropped malformed DNS query", e) }
    }

    private fun handleDns(pkt: IpPacket) {
        val query = DnsMessage.parseQuery(pkt.payload)
        val m = matcher
        val response: ByteArray = if (query == null) {
            forward(pkt.payload) ?: return
        } else if (query.qname in DOH_CANARIES) {
            // Tells Firefox not to switch on its own DNS-over-HTTPS, which would bypass filtering.
            DnsMessage.nxDomain(pkt.payload, query)
        } else if (m.isBlocked(query.qname)) {
            VpnState.record(query.qname, true)
            debugLog(true, query.qname)
            DnsMessage.blockedResponse(pkt.payload, query)
        } else {
            val upstream = forward(pkt.payload)
            // CNAME cloaking: "metrics.news-site.com" may just be an alias for an ad/tracker domain.
            val cloaked = upstream != null && !m.isAllowed(query.qname) &&
                DnsMessage.cnameTargets(upstream).any { m.isBlocked(it) }
            VpnState.record(query.qname, cloaked)
            debugLog(cloaked, query.qname)
            when {
                cloaked -> DnsMessage.blockedResponse(pkt.payload, query)
                upstream != null -> upstream
                else -> DnsMessage.servFail(pkt.payload, query)
            }
        }
        write(IpPacket.buildUdp(pkt.dstAddr, pkt.srcAddr, 53, pkt.srcPort, response))
    }

    /**
     * Tries the chosen resolvers, then the network's own DNS (covers Wi-Fi that blocks outside DNS,
     * captive portals and carriers without IPv4). Starts from whichever server answered last.
     */
    private fun forward(payload: ByteArray): ByteArray? {
        val servers = (configuredUpstreams + networkDns).distinct()
            .ifEmpty { listOf(InetAddress.getByName("1.1.1.1")) }
        val start = preferred.coerceIn(0, servers.size - 1)
        for (k in servers.indices) {
            val i = (start + k) % servers.size
            val server = servers[i]
            try {
                DatagramSocket().use { sock ->
                    protect(sock)
                    sock.soTimeout = 2500
                    // connect(): the OS drops datagrams from anyone but this server, so an
                    // attacker can't inject answers by guessing our port.
                    sock.connect(server, 53)
                    sock.send(DatagramPacket(payload, payload.size))
                    val rbuf = ByteArray(4096)
                    while (true) {
                        val resp = DatagramPacket(rbuf, rbuf.size)
                        sock.receive(resp)
                        val r = rbuf.copyOf(resp.length)
                        // Only accept the answer to *this* query (same ID, marked as a response).
                        if (DnsMessage.isResponseTo(payload, r)) {
                            preferred = i
                            return r
                        }
                    }
                }
            } catch (e: SocketTimeoutException) {
                Log.d(TAG, "Upstream $server timed out")
            } catch (e: IOException) {
                Log.d(TAG, "Upstream $server failed: ${e.message}")
            }
        }
        return null
    }

    /** Debug builds log every decision so scripts/device-adblock-test.sh can check them. */
    private fun debugLog(blocked: Boolean, name: String) {
        if (BuildConfig.DEBUG) Log.d(DNS_LOG_TAG, (if (blocked) "BLOCK " else "ALLOW ") + name)
    }

    private fun write(packet: ByteArray) {
        val o = out ?: return
        synchronized(o) {
            try { o.write(packet) } catch (e: IOException) { }
        }
    }

    private fun shutdown(stopService: Boolean = true) {
        if (tun != null) settings.lifetimeBlocked = settings.lifetimeBlocked + VpnState.stats.value.blocked
        networkCallback?.let { cb ->
            runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
        }
        networkCallback = null
        readerThread?.interrupt()
        readerThread = null
        workers?.shutdownNow()
        workers = null
        refresher?.shutdownNow()
        refresher = null
        runCatching { tun?.close() }
        tun = null
        out = null
        if (stopService) {
            if (VpnState.status.value != Status.ERROR) VpnState.setStatus(Status.STOPPED)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            TileUpdater.request(this)
        }
    }

    override fun onRevoke() {
        // Another VPN took over, or the user revoked permission in system settings.
        settings.wantEnabled = false
        shutdown()
    }

    override fun onDestroy() {
        if (tun != null) shutdown()
        super.onDestroy()
    }

    // ---- notification ----

    private fun goForeground() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Protection status", NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) }
            )
        }
        val stopIntent = PendingIntent.getService(
            this, 1, Intent(this, AdBlockVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("AdWizz is blocking ads")
            .setContentText("Filtering DNS for every app on this device")
            .setContentIntent(mainActivityIntent())
            .addAction(0, "Turn off", stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
    }

    private fun mainActivityIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    companion object {
        private const val TAG = "AdWizzVpn"
        const val DNS_LOG_TAG = "AdWizzDns"
        private const val CHANNEL_ID = "vpn_status"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "rocks.chirag.adshield.START"
        const val ACTION_STOP = "rocks.chirag.adshield.STOP"
        const val ACTION_RELOAD = "rocks.chirag.adshield.RELOAD"
        const val ACTION_RESTART = "rocks.chirag.adshield.RESTART"

        private const val TUN_ADDRESS4 = "10.111.222.2"
        private const val FAKE_DNS4 = "10.111.222.1"
        private const val TUN_ADDRESS6 = "fdad:5e1d::2"
        private const val FAKE_DNS6 = "fdad:5e1d::1"

        /** Domains whose NXDOMAIN answer tells browsers to keep using system DNS. */
        private val DOH_CANARIES = setOf("use-application-dns.net")

        /**
         * Public resolvers that apps and browsers hard-code (plain DNS, DoT or DoH) to dodge the
         * system DNS. Plain DNS to these is filtered; encrypted DNS is refused so they fall back.
         * Entries may be "ip" or "ip/prefix".
         */
        private val HARDCODED_RESOLVERS4 = listOf(
            "8.8.8.8", "8.8.4.4",                                       // Google
            "1.1.1.1", "1.0.0.1", "1.1.1.2", "1.0.0.2", "1.1.1.3", "1.0.0.3", // Cloudflare
            "9.9.9.9", "149.112.112.112", "9.9.9.10", "149.112.112.10",
            "9.9.9.11", "149.112.112.11",                               // Quad9
            "208.67.222.222", "208.67.220.220", "208.67.222.123", "208.67.220.123", // OpenDNS
            "94.140.14.14", "94.140.15.15", "94.140.14.15", "94.140.15.16",
            "94.140.14.140", "94.140.14.141",                           // AdGuard
            "4.2.2.1", "4.2.2.2", "4.2.2.3", "4.2.2.4", "4.2.2.5", "4.2.2.6", // Level3
            "45.90.28.0/24", "45.90.30.0/24",                           // NextDNS
            "76.76.2.0/24", "76.76.10.0/24",                            // Control D
            "185.228.168.0/24", "185.228.169.0/24",                     // CleanBrowsing
            "194.242.2.0/24",                                           // Mullvad
            "185.222.222.222", "45.11.45.11",                           // DNS.SB
            "77.88.8.8", "77.88.8.1",                                   // Yandex
            "8.26.56.26", "8.20.247.20",                                // Comodo
            "223.5.5.5", "223.6.6.6",                                   // AliDNS
            "114.114.114.114", "114.114.115.115",                       // 114DNS
            "119.29.29.29",                                             // DNSPod
        )
        private val HARDCODED_RESOLVERS6 = listOf(
            "2001:4860:4860::8888", "2001:4860:4860::8844",
            "2606:4700:4700::1111", "2606:4700:4700::1001",
            "2606:4700:4700::1112", "2606:4700:4700::1002",
            "2606:4700:4700::1113", "2606:4700:4700::1003",
            "2620:fe::fe", "2620:fe::9", "2620:fe::10", "2620:fe::11",
            "2620:119:35::35", "2620:119:53::53",
            "2a10:50c0::ad1:ff", "2a10:50c0::ad2:ff",
            "2a07:a8c0::/32", "2a07:a8c1::/32",                         // NextDNS
            "2606:1a40::/32",                                           // Control D
            "2a0d:2a00:1::/48", "2a0d:2a00:2::/48",                     // CleanBrowsing
            "2a07:e340::/32",                                           // Mullvad
        )

        fun send(context: Context, action: String) {
            val i = Intent(context, AdBlockVpnService::class.java).setAction(action)
            if (action == ACTION_START) ContextCompat.startForegroundService(context, i)
            else context.startService(i)
        }
    }
}
