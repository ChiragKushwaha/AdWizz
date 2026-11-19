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

    /** Packages users know by name, preferred when they share an app ID with helper packages. */
    private val MAIN_PACKAGES = listOf("com.google.android.gms")

    /** The package to name a shared app ID after: a well-known one, else the first alphabetically. */
    internal fun mainPackage(packages: List<String>): String? =
        MAIN_PACKAGES.firstOrNull { it in packages } ?: packages.minOrNull()

    fun label(context: Context, pkg: String?): String {
        if (pkg == null || pkg == UNKNOWN) return tr("stats.unknown_app")
        if (pkg.startsWith(UID_PREFIX)) {
            val uid = pkg.removePrefix(UID_PREFIX).toIntOrNull() ?: return tr("stats.unknown_app")
            if (uid % 100_000 < Process.FIRST_APPLICATION_UID) return tr("stats.system")
            // An app ID shared by several packages (e.g. Play services + Services Framework): name it
            // after its main package rather than calling it unknown.
            return labels.getOrPut(pkg) {
                val shared = runCatching { context.packageManager.getPackagesForUid(uid) }.getOrNull()?.toList().orEmpty()
                mainPackage(shared)?.let { label(context, it) } ?: tr("stats.unknown_app")
            }
        }
        return labels.getOrPut(pkg) {
            runCatching {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
            }.getOrDefault(pkg)
        }
    }
}
