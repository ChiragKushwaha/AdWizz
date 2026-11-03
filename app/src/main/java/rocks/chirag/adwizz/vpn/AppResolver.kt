package rocks.chirag.adwizz.vpn

import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.system.OsConstants
import rocks.chirag.adwizz.core.IpPacket
import rocks.chirag.adwizz.data.AppNames
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds which app sent a DNS query, so the dashboard can show blocks per app. Android 10+ lets the
 * active VPN ask who owns a connection through it; older versions return null ("unknown app").
 * Android's resolver sends look-ups on the app's behalf but tags the socket with the app's ID.
 */
internal class AppResolver(
    private val cm: ConnectivityManager?,
    private val packagesForUid: (Int) -> Array<String>?,
) {
    private val byUid = ConcurrentHashMap<Int, String>()

    fun packageFor(pkt: IpPacket): String? {
        val cm = cm ?: return null
        if (Build.VERSION.SDK_INT < 29 || pkt.srcPort < 0) return null
        val uid = runCatching {
            cm.getConnectionOwnerUid(
                OsConstants.IPPROTO_UDP,
                InetSocketAddress(InetAddress.getByAddress(pkt.srcAddr), pkt.srcPort),
                InetSocketAddress(InetAddress.getByAddress(pkt.dstAddr), pkt.dstPort),
            )
        }.getOrNull() ?: return null
        if (uid == Process.INVALID_UID || uid < 0) return null
        return byUid.getOrPut(uid) {
            // Several system packages can share one ID: name it by the ID instead of picking one.
            packagesForUid(uid)?.singleOrNull() ?: AppNames.uidKey(uid)
        }
    }
}
