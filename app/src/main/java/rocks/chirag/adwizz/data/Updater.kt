package rocks.chirag.adwizz.data

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rocks.chirag.adwizz.BuildConfig
import java.io.File

/**
 * Self-updates from GitHub Releases (the app isn't on Google Play, so Play's in-app updates don't
 * apply). Check the manifest → download in the background → verify SHA-256 and that it's signed
 * with this app's own key → one tap (or the notification) installs it.
 */
object Updater {
    private const val TAG = "AdWizzUpdate"
    private const val CHECK_EVERY_MS = 24L * 60 * 60 * 1000

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun isNewer(info: UpdateInfo, current: Int = BuildConfig.VERSION_CODE) = info.versionCode > current

    /** Checks at most once a day unless [force]; returns a newer release if there is one. Blocking. */
    fun check(context: Context, force: Boolean = false): UpdateInfo? {
        val prefs = context.getSharedPreferences("adwizz", Context.MODE_PRIVATE)
        if (!force && System.currentTimeMillis() - prefs.getLong("update_checked", 0) < CHECK_EVERY_MS) {
            return state.value.info
        }
        // Offline / server trouble: keep whatever we already know (e.g. an update that's downloaded).
        val latest = runCatching { UpdateManifest.parse(UpdateDownload.text(UpdateManifest.URL, 64 * 1024)) }
            .onFailure { Log.w(TAG, "Update check failed", it) }.getOrNull() ?: return state.value.info
        prefs.edit { putLong("update_checked", System.currentTimeMillis()) }
        val info = latest.takeIf { isNewer(it) }
        if (info == null) _state.value = UpdateState.Idle
        else if (state.value.info?.versionCode != info.versionCode) _state.value = UpdateState.Available(info)
        return info
    }

    /** Downloads and verifies [info] so installing is instant. Returns the APK, or null on failure. Blocking. */
    fun prepare(context: Context, info: UpdateInfo): File? {
        (state.value as? UpdateState.Ready)?.takeIf { it.info == info && it.apk.exists() }?.let { return it.apk }
        return try {
            _state.value = UpdateState.Downloading(info, 0f)
            val dir = File(context.applicationContext.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
            val apk = File(dir, "AdWizz-${info.versionCode}.apk")
            UpdateDownload.file(info.apkUrl, apk, info.sizeBytes) { _state.value = UpdateState.Downloading(info, it) }
            check(UpdateDownload.sha256(apk) == info.sha256) { "Download was corrupted (checksum mismatch)" }
            check(ApkInstaller.isOurs(context, apk)) { "Update isn't signed by AdWizz" }
            _state.value = UpdateState.Ready(info, apk)
            apk
        } catch (e: Exception) {
            Log.w(TAG, "Update download failed", e)
            _state.value = UpdateState.Failed(info, e.message ?: "Download failed")
            null
        }
    }

    /** Downloads (if needed) and installs. Blocking — call off the main thread. */
    fun install(context: Context, info: UpdateInfo) {
        val apk = prepare(context, info) ?: return
        try {
            _state.value = UpdateState.Installing(info)
            Telemetry.event(context, "update_install", "to_version" to info.versionName)
            ApkInstaller.install(context.applicationContext, apk)
        } catch (e: Exception) {
            Log.w(TAG, "Update install failed", e)
            Telemetry.nonFatal(e)
            _state.value = UpdateState.Failed(info, e.message ?: "Install failed")
        }
    }

    fun dismiss() { _state.value = UpdateState.Idle }

    /** Download found updates in the background right away (tests switch this off). */
    @Volatile var autoDownload = true

    @androidx.annotation.VisibleForTesting
    internal fun setStateForTest(state: UpdateState) { _state.value = state }

    /** True once the user let AdWizz install apps (a one-time Android setting). */
    fun canInstall(context: Context) = Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    internal fun onInstallFailed(message: String) {
        _state.value = UpdateState.Failed(state.value.info, message)
    }
}
