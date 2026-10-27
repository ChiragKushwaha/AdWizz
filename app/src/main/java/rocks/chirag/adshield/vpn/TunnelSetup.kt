package rocks.chirag.adshield.vpn

import android.app.PendingIntent
import android.net.VpnService
import android.os.Build
import rocks.chirag.adshield.data.Settings

/** Addresses inside the tunnel. Apps are told the fake DNS servers are their DNS. */
internal object TunnelAddresses {
    const val TUN4 = "10.111.222.2"
    const val FAKE_DNS4 = "10.111.222.1"
    const val TUN6 = "fdad:5e1d::2"
    const val FAKE_DNS6 = "fdad:5e1d::1"

    /** True for our own fake DNS addresses (never use them as an upstream). */
    fun isOurs(address: String) = address == FAKE_DNS4 || address.startsWith("fdad:5e1d")
}

/**
 * Configures the tunnel: only the fake DNS (and, optionally, well-known public resolvers) is
 * routed into it, so all other traffic keeps flowing over the normal network at full speed.
 */
internal fun VpnService.Builder.configureForDns(
    sessionName: String,
    settings: Settings,
    ownPackage: String,
    configureIntent: PendingIntent,
): VpnService.Builder {
    setSession(sessionName)
    setMtu(1500)
    addAddress(TunnelAddresses.TUN4, 32)
    addDnsServer(TunnelAddresses.FAKE_DNS4)
    addRoute(TunnelAddresses.FAKE_DNS4, 32)
    setBlocking(true)
    setConfigureIntent(configureIntent)

    // IPv6: some networks (e.g. IPv6-first carriers) query DNS over IPv6. Kernels without IPv6
    // throw here; IPv4-only filtering still works in that case.
    val v6 = runCatching {
        addAddress(TunnelAddresses.TUN6, 128)
        addDnsServer(TunnelAddresses.FAKE_DNS6)
        addRoute(TunnelAddresses.FAKE_DNS6, 128)
    }.isSuccess

    if (settings.catchHardcodedDns) {
        ResolverLists.IPV4.forEach { addRouteEntry(it, 32) }
        if (v6) ResolverLists.IPV6.forEach { addRouteEntry(it, 128) }
    }
    // Our own traffic (list downloads, upstream DNS) must never loop back into the tunnel.
    runCatching { addDisallowedApplication(ownPackage) }
    // Excluded apps use the normal network directly, so the VPN isn't their active network.
    settings.excludedApps.forEach { pkg -> runCatching { addDisallowedApplication(pkg) } }   // throws if uninstalled
    if (Build.VERSION.SDK_INT >= 29) setMetered(false)
    return this
}

/** Adds "ip" or "ip/prefix" as a route; bad entries are skipped. */
private fun VpnService.Builder.addRouteEntry(entry: String, defaultPrefix: Int) {
    val ip = entry.substringBefore('/')
    val prefix = entry.substringAfter('/', "").toIntOrNull() ?: defaultPrefix
    runCatching { addRoute(ip, prefix) }
}
