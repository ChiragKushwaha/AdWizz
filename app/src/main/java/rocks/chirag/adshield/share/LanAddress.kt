package rocks.chirag.adshield.share

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface

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
