package rocks.chirag.adwizz.vpn

import rocks.chirag.adwizz.tr
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.content.ContextCompat
import rocks.chirag.adwizz.R
import rocks.chirag.adwizz.core.DomainMatcher
import rocks.chirag.adwizz.data.BlocklistRepository
import rocks.chirag.adwizz.data.Settings
import rocks.chirag.adwizz.data.Status
import rocks.chirag.adwizz.data.Telemetry
import rocks.chirag.adwizz.data.VpnState

/** On-device VPN that only carries DNS ([configureForDns]); blocking in [PacketPump], forwarding in [UpstreamDns]. */
class AdBlockVpnService : VpnService() {
    private lateinit var settings: Settings
    private lateinit var repo: BlocklistRepository
    private val upstream = UpstreamDns { protect(it) }
    @Volatile private var matcher = DomainMatcher(emptySet(), emptySet())

    private var tun: ParcelFileDescriptor? = null
    private var pump: PacketPump? = null
    private var maintenance: Maintenance? = null
    private var network: NetworkWatcher? = null
    @Volatile private var starting = false

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        repo = BlocklistRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { settings.wantEnabled = false; shutdown(); return START_NOT_STICKY }
            // A list switched on in Settings may not be downloaded yet.
            ACTION_RELOAD -> if (tun != null) Thread { reloadConfig(); maintenance?.refreshLists() }.start()
            ACTION_RESTART -> if (tun != null || starting) { shutdown(stopService = false); startTunnel() }
            // ACTION_START, or a null intent when the system restarts us / Always-on VPN kicks in.
            else -> {
                settings.wantEnabled = true
                VpnNotification.showForeground(this, ACTION_STOP)   // must happen promptly, even if running
                if (tun == null && !starting) startTunnel()
            }
        }
        return START_STICKY
    }

    private fun startTunnel() {
        starting = true
        VpnState.setStatus(Status.STARTING)
        VpnNotification.showForeground(this, ACTION_STOP)
        Thread {
            try {
                reloadConfig(); establish()
                Telemetry.event(this, "protection_on", "list_size" to matcher.size())
                network = NetworkWatcher(getSystemService(ConnectivityManager::class.java)) { upstream.refreshNetworkDns(it) }
                maintenance = Maintenance(applicationContext, repo, ::reloadConfig).also { it.start() }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start", e); Telemetry.nonFatal(e)
                VpnState.setStatus(Status.ERROR, e.message ?: tr("home.vpn_failed"))
                shutdown()
            } finally {
                starting = false
            }
        }.start()
    }

    private fun establish() {
        val pfd = Builder().configureForDns(getString(R.string.app_name), settings, packageName, VpnNotification.openApp(this))
            .establish() ?: throw IllegalStateException("VPN permission not granted")
        tun = pfd
        pump = PacketPump(pfd, { matcher }, upstream).also { it.start() }
        VpnState.resetSession()
        VpnState.setStatus(Status.RUNNING)
        TileUpdater.request(this)
    }

    private fun reloadConfig() {
        matcher = repo.buildMatcher()
        upstream.configure(settings)
        upstream.refreshNetworkDns(getSystemService(ConnectivityManager::class.java))
        Log.i(TAG, "Loaded ${matcher.size()} blocked domains, $upstream")
    }

    private fun shutdown(stopService: Boolean = true) {
        if (tun != null) settings.lifetimeBlocked += VpnState.stats.value.blocked
        network?.close(); network = null
        pump?.stop(); pump = null
        maintenance?.close(); maintenance = null
        runCatching { tun?.close() }; tun = null
        if (!stopService) return
        Telemetry.event(this, "protection_off", "blocked" to VpnState.stats.value.blocked)
        if (VpnState.status.value != Status.ERROR) VpnState.setStatus(Status.STOPPED)
        VpnNotification.remove(this)
        stopSelf()
        TileUpdater.request(this)
    }

    /** Another VPN took over, or the user revoked permission in system settings. */
    override fun onRevoke() { settings.wantEnabled = false; shutdown() }
    override fun onDestroy() { if (tun != null) shutdown(); super.onDestroy() }

    companion object {
        private const val TAG = "AdWizzVpn"
        const val ACTION_START = "rocks.chirag.adwizz.START"
        const val ACTION_STOP = "rocks.chirag.adwizz.STOP"
        const val ACTION_RELOAD = "rocks.chirag.adwizz.RELOAD"
        const val ACTION_RESTART = "rocks.chirag.adwizz.RESTART"

        fun send(context: Context, action: String) {
            val i = Intent(context, AdBlockVpnService::class.java).setAction(action)
            if (action == ACTION_START) ContextCompat.startForegroundService(context, i) else context.startService(i)
        }
    }
}
