package rocks.chirag.adwizz.data

import rocks.chirag.adwizz.tr
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import rocks.chirag.adwizz.R
import rocks.chirag.adwizz.ui.MainActivity

/**
 * Background side of updates (runs from the VPN's hourly maintenance, so users hear about fixes
 * even if they never open the app): finds a newer release, downloads + verifies it, then posts
 * "AdWizz vX is ready". Tapping the notification opens the app and installs straight away.
 */
object UpdateNotifier {
    /** Intent extra that makes MainActivity install the prepared update immediately. */
    const val EXTRA_INSTALL = "rocks.chirag.adwizz.INSTALL_UPDATE"
    private const val CHANNEL = "updates"
    private const val ID = 2

    fun checkAndNotify(context: Context) {
        val info = Updater.check(context) ?: return
        Updater.prepare(context, info) ?: return
        val prefs = context.getSharedPreferences("adwizz", Context.MODE_PRIVATE)
        if (prefs.getInt("update_notified", 0) == info.versionCode) return   // once per version
        if (notify(context, info)) prefs.edit { putInt("update_notified", info.versionCode) }
    }

    private fun notify(context: Context, info: UpdateInfo): Boolean {
        val nm = NotificationManagerCompat.from(context)
        val allowed = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!allowed || !nm.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL, tr("notif.channel_updates"), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val open = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java).putExtra(EXTRA_INSTALL, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(tr("notif.update_title", "version" to info.versionName))
            .setContentText(info.notes.ifBlank { tr("notif.update_text") })
            .setStyle(NotificationCompat.BigTextStyle().bigText(info.notes.ifBlank { tr("notif.update_short") }))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        return runCatching { nm.notify(ID, n) }.isSuccess   // SecurityException if permission revoked
    }

    fun cancel(context: Context) = NotificationManagerCompat.from(context).cancel(ID)
}
