package rocks.chirag.adshield.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import rocks.chirag.adshield.R
import rocks.chirag.adshield.ui.MainActivity

/** The ongoing "AdWizz is blocking ads" notification every foreground VPN must show. */
internal object VpnNotification {
    private const val CHANNEL_ID = "vpn_status"
    private const val ID = 1

    fun showForeground(service: Service, stopAction: String) {
        if (Build.VERSION.SDK_INT >= 26) {
            service.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Protection status", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
            )
        }
        val stop = PendingIntent.getService(
            service, 1, Intent(service, AdBlockVpnService::class.java).setAction(stopAction),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification: Notification = NotificationCompat.Builder(service, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("AdWizz is blocking ads")
            .setContentText("Filtering DNS for every app on this device")
            .setContentIntent(openApp(service))
            .addAction(0, "Turn off", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(service, ID, notification, type)
    }

    fun remove(service: Service) = ServiceCompat.stopForeground(service, ServiceCompat.STOP_FOREGROUND_REMOVE)

    /** Opens AdWizz's main screen. */
    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
