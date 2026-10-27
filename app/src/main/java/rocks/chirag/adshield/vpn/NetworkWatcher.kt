package rocks.chirag.adshield.vpn

import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.os.Build

/** Calls [onChange] whenever the real (Wi-Fi / mobile) network or its DNS servers change. */
internal class NetworkWatcher(
    private val cm: ConnectivityManager?,
    private val onChange: (ConnectivityManager?) -> Unit,
) : AutoCloseable {
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onChange(cm)
        override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) = onChange(cm)
    }
    private val registered = Build.VERSION.SDK_INT >= 24 &&
        cm?.let { runCatching { it.registerDefaultNetworkCallback(callback) }.isSuccess } == true

    override fun close() {
        if (registered) runCatching { cm?.unregisterNetworkCallback(callback) }
    }
}
