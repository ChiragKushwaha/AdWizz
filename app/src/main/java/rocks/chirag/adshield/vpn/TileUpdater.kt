package rocks.chirag.adshield.vpn

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.quicksettings.TileService

/**
 * Kept separate from [ToggleTileService] so code running on Android 6 (no Quick Settings tile API)
 * never loads a class that extends TileService.
 */
object TileUpdater {
    fun request(context: Context) {
        if (Build.VERSION.SDK_INT < 24) return
        runCatching {
            TileService.requestListeningState(
                context, ComponentName(context.packageName, "rocks.chirag.adshield.vpn.ToggleTileService")
            )
        }
    }
}
