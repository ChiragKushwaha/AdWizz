package rocks.chirag.adwizz.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/** Verifies a downloaded APK and installs it with Android's PackageInstaller. */
internal object ApkInstaller {
    private const val ACTION_RESULT = "rocks.chirag.adwizz.INSTALL_RESULT"

    private const val EXTRA_TARGET = "target"
    const val TARGET_SELF = "self"
    const val TARGET_ADDON = "addon"

    /**
     * The APK must be [packageName] (this app by default), signed with exactly this app's
     * certificate (for updates Android checks too; for the add-on only this check stands).
     */
    @Suppress("DEPRECATION")
    fun isOurs(context: Context, apk: File, packageName: String = context.packageName): Boolean {
        val pm = context.packageManager
        val flag = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        fun certs(info: PackageInfo?): Set<String> = when {
            info == null -> emptySet()
            Build.VERSION.SDK_INT >= 28 -> info.signingInfo?.apkContentsSigners.orEmpty().map { it.toCharsString() }.toSet()
            else -> info.signatures.orEmpty().map { it.toCharsString() }.toSet()
        }
        val theirs = pm.getPackageArchiveInfo(apk.path, flag)
        val mine = certs(pm.getPackageInfo(context.packageName, flag))
        return theirs?.packageName == packageName && mine.isNotEmpty() && mine == certs(theirs)
    }

    /**
     * Starts the install. On Android 12+ it asks to skip the "Update this app?" screen: Android
     * honours that once AdWizz installed the previous version itself (every update after the first).
     */
    fun install(context: Context, apk: File, packageName: String = context.packageName) {
        val target = if (packageName == context.packageName) TARGET_SELF else TARGET_ADDON
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(packageName)
            setSize(apk.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("AdWizz.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallResultReceiver::class.java).setAction(ACTION_RESULT)
                .putExtra(EXTRA_TARGET, target)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val requestCode = if (target == TARGET_SELF) 7 else 8
            session.commit(PendingIntent.getBroadcast(context, requestCode, intent, flags).intentSender)
        }
    }
}

/** PackageInstaller results: shows Android's confirm screen when it asks for one. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val addon = intent.getStringExtra("target") == ApkInstaller.TARGET_ADDON
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)
                confirm?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            // The app is replaced and restarts on the new version; BootReceiver turns protection back on.
            PackageInstaller.STATUS_SUCCESS -> if (addon) FocusAddon.onInstalled()
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Install failed ($status)"
                if (addon) FocusAddon.onInstallFailed(status, message) else Updater.onInstallFailed(message)
            }
        }
    }
}
