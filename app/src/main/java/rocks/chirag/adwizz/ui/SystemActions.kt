package rocks.chirag.adwizz.ui

import android.content.Context
import rocks.chirag.adwizz.share.ShareServer
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import rocks.chirag.adwizz.data.*
import rocks.chirag.adwizz.vpn.AdBlockVpnService

internal fun reload(context: Context) {
    if (VpnState.status.value == Status.RUNNING) {
        AdBlockVpnService.send(context, AdBlockVpnService.ACTION_RELOAD)
    }
}

internal fun isIpv4(s: String): Boolean {
    val parts = s.split('.')
    return parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
}

internal fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

internal fun isBatteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

/**
 * Opens the system's battery-optimisation list so the user can set AdWizz to "Unrestricted".
 * (Asking directly needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, which Play restricts.)
 */
internal fun requestBatteryExemption(context: Context) {
    runCatching { context.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        .onFailure { openAppDetails(context) }
}

internal fun openAppDetails(context: Context) {
    runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
        )
    }
}

/** Phone makers that aggressively kill background apps each hide the switch in a different place. */
internal fun oemHint(): String = when (Build.MANUFACTURER.lowercase()) {
    "xiaomi", "redmi", "poco" ->
        "On Xiaomi/Redmi/POCO: App settings → Autostart ON, and Battery saver → No restrictions. Also lock AdWizz in Recents."
    "oppo", "realme", "oneplus" ->
        "On Oppo/Realme/OnePlus: App settings → Battery → Allow background activity, and allow Auto launch."
    "vivo", "iqoo" ->
        "On Vivo/iQOO: Settings → Battery → Background power consumption → allow AdWizz, and enable Autostart."
    "samsung" ->
        "On Samsung: App settings → Battery → Unrestricted, and keep AdWizz out of 'Sleeping apps'."
    "huawei", "honor" ->
        "On Huawei/Honor: Battery → App launch → AdWizz → Manage manually, all switches on."
    else ->
        "Set AdWizz's battery usage to Unrestricted so the system doesn't stop it in the background."
}

/** True when Private DNS is set to a specific hostname, which makes Android bypass any VPN's DNS. */
fun isPrivateDnsStrict(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 28) return false
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    return runCatching { cm.getLinkProperties(cm.activeNetwork)?.privateDnsServerName != null }.getOrDefault(false)
}

internal fun open(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action)) }
        .onFailure { context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS)) }
}

/** Rebuilds the tunnel (needed for routing / excluded-app changes) if protection is on. */
internal fun restartVpnIfRunning(context: Context) {
    if (VpnState.status.value == Status.RUNNING) {
        AdBlockVpnService.send(context, AdBlockVpnService.ACTION_RESTART)
    }
}

/** The setup page's server, one per app process so it outlives a screen rotation. */
internal object SetupServer { val instance = ShareServer() }

/** The Activity behind a Compose context (it may be wrapped, e.g. by a theme). */
internal fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) {
        if (c is android.app.Activity) return c
        c = c.baseContext
    }
    return null
}
