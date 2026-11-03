package rocks.chirag.adwizz.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import rocks.chirag.adwizz.tr
import java.util.concurrent.ConcurrentHashMap

/** Turns the package names the VPN records into names people recognise ("com.spotify.music" -> "Spotify"). */
object AppNames {
    /** Stands in for an app the VPN couldn't identify (Android 9 and older, or a vanished app). */
    const val UNKNOWN = "?"
    private const val UID_PREFIX = "uid:"
    private val labels = ConcurrentHashMap<String, String>()

    /** Key for an Android user ID shared by several system packages. */
    fun uidKey(uid: Int) = UID_PREFIX + uid

    fun label(context: Context, pkg: String?): String {
        if (pkg == null || pkg == UNKNOWN) return tr("stats.unknown_app")
        if (pkg.startsWith(UID_PREFIX)) {
            val uid = pkg.removePrefix(UID_PREFIX).toIntOrNull() ?: return tr("stats.unknown_app")
            return if (uid % 100_000 < Process.FIRST_APPLICATION_UID) tr("stats.system") else tr("stats.unknown_app")
        }
        return labels.getOrPut(pkg) {
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
            }.getOrDefault(pkg)
        }
    }
}
