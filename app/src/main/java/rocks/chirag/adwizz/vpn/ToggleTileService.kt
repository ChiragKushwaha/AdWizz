package rocks.chirag.adwizz.vpn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import rocks.chirag.adwizz.data.Status
import rocks.chirag.adwizz.data.VpnState
import rocks.chirag.adwizz.ui.MainActivity

/** Quick Settings tile to flip protection on/off without opening the app. */
// Tiles exist from Android 7 (API 24); the manifest only enables this service there
// (@bool/tile_supported is true only in values-v24).
@androidx.annotation.RequiresApi(24)
class ToggleTileService : TileService() {

    override fun onStartListening() {
        refresh()
    }

    override fun onClick() {
        val running = VpnState.status.value == Status.RUNNING || VpnState.status.value == Status.STARTING
        if (running) {
            AdBlockVpnService.send(this, AdBlockVpnService.ACTION_STOP)
        } else if (VpnService.prepare(this) != null) {
            // Need the one-time system consent dialog, which only an activity can show.
            openApp()
        } else {
            // Some OEM builds refuse background foreground-service starts; fall back to the app.
            runCatching { AdBlockVpnService.send(this, AdBlockVpnService.ACTION_START) }
                .onFailure { openApp(); return }
        }
        qsTile?.apply { state = if (running) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; updateTile() }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.state = when (VpnState.status.value) {
            Status.RUNNING, Status.STARTING -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
