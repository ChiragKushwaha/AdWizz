package rocks.chirag.adwizz.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import rocks.chirag.adwizz.data.Settings

/**
 * Turns protection back on after a reboot or an app update, if it was on before.
 * (Always-on VPN does this too, but many people never enable it.)
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",     // some Xiaomi/HTC builds
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }
        if (!Settings(context).wantEnabled) return
        // prepare() returns null only if VPN consent is still granted; otherwise the user must open the app.
        if (VpnService.prepare(context) != null) return
        runCatching { AdBlockVpnService.send(context, AdBlockVpnService.ACTION_START) }
            .onFailure { Log.w("AdWizzBoot", "Could not start after boot", it) }
    }
}
