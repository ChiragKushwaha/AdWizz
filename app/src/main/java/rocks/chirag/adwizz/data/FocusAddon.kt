package rocks.chirag.adwizz.data

import rocks.chirag.adwizz.tr
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageInstaller
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rocks.chirag.adwizz.BuildConfig
import java.io.File

sealed interface AddonState {
    data object Idle : AddonState
    data class Downloading(val progress: Float) : AddonState
    data object Installing : AddonState
    /** [blocked]: Play Protect (or another policy) refused it, so retrying won't help by itself. */
    data class Failed(val message: String, val blocked: Boolean) : AddonState
}

/**
 * The optional "AdWizz Add-on" add-on. It holds the Accessibility service that leaves
 * Reels and Shorts. That service lives in its own app because Play Protect blocks any downloaded
 * app that can use Accessibility, and AdWizz itself shouldn't be blocked for it.
 * AdWizz downloads it from the same GitHub release, checks it's signed with AdWizz's own key,
 * and installs it.
 */
object FocusAddon {
    private const val TAG = "AdWizzAddon"
    val PACKAGE: String = BuildConfig.FOCUS_PACKAGE
    private val SERVICE = ComponentName(PACKAGE, "rocks.chirag.adwizz.focus.ShortVideoBlockerService")

    private val _state = MutableStateFlow<AddonState>(AddonState.Idle)
    val state: StateFlow<AddonState> = _state.asStateFlow()

    /** The newest add-on on GitHub, once [checkLatest] has run. */
    private val _latest = MutableStateFlow<AddonInfo?>(null)
    val latest: StateFlow<AddonInfo?> = _latest.asStateFlow()

    /** Bumped when an install finishes, so screens re-check [isInstalled]. */
    private val _installs = MutableStateFlow(0)
    val installs: StateFlow<Int> = _installs.asStateFlow()

    @Suppress("DEPRECATION")
    fun installedVersion(context: Context): Long? = runCatching {
        val info = context.packageManager.getPackageInfo(PACKAGE, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }.getOrNull()

    fun isInstalled(context: Context) = installedVersion(context) != null

    /** e.g. "1.1.0", or null when the add-on isn't installed. */
    fun installedVersionName(context: Context): String? = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0).versionName ?: "?"
    }.getOrNull()

    /** True if the user has turned the add-on's service on in Accessibility settings. */
    fun isServiceEnabled(context: Context): Boolean {
        val enabled = AndroidSettings.Secure.getString(
            context.contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == SERVICE }
    }

    /** Looks up the newest add-on version. Blocking; null if offline. */
    fun checkLatest(): AddonInfo? = runCatching {
        UpdateManifest.parseAddon(UpdateDownload.text(UpdateManifest.URL, 64 * 1024))
    }.getOrNull()?.also { _latest.value = it }

    /** True when a newer add-on than the installed one is known. */
    fun updateAvailable(context: Context): Boolean {
        val installed = installedVersion(context) ?: return false
        return (_latest.value?.versionCode ?: 0) > installed
    }

    /**
     * Daily, from the VPN's maintenance: updates an installed add-on in the background. Only on
     * Android 12+, where AdWizz (the add-on's installer) can update it without a prompt.
     */
    @Volatile private var lastBackgroundCheck = 0L

    fun updateInBackground(context: Context, now: Long = System.currentTimeMillis()) {
        if (Build.VERSION.SDK_INT < 31 || !isInstalled(context) || !Updater.canInstall(context)) return
        if (now - lastBackgroundCheck < 24 * 60 * 60 * 1000L) return
        lastBackgroundCheck = now
        checkLatest()
        if (updateAvailable(context)) install(context)
    }

    /** Android's "Uninstall this app?" screen for the add-on. */
    fun uninstallIntent(): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$PACKAGE")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Downloads, verifies and installs the latest add-on (if it isn't installed or is older). Blocking. */
    fun install(context: Context) {
        val app = context.applicationContext
        if (_state.value is AddonState.Downloading || _state.value is AddonState.Installing) return
        try {
            _state.value = AddonState.Downloading(0f)
            val info = UpdateManifest.parseAddon(UpdateDownload.text(UpdateManifest.URL, 64 * 1024))
                ?.also { _latest.value = it } ?: error(tr("addon.unavailable"))
            if ((installedVersion(app) ?: 0) >= info.versionCode) { onInstalled(); return }
            val dir = File(app.cacheDir, "addon").apply { deleteRecursively(); mkdirs() }
            val apk = File(dir, "AdWizzFocus-${info.versionCode}.apk")
            UpdateDownload.file(info.apkUrl, apk, info.sizeBytes) { _state.value = AddonState.Downloading(it) }
            check(UpdateDownload.sha256(apk) == info.sha256) { tr("download.corrupt") }
            check(ApkInstaller.isOurs(app, apk, PACKAGE)) { tr("addon.unsigned") }
            _state.value = AddonState.Installing
            Telemetry.event(app, "focus_addon_install")
            ApkInstaller.install(app, apk, PACKAGE)
        } catch (e: Exception) {
            Log.w(TAG, "Add-on install failed", e)
            _state.value = AddonState.Failed(e.message ?: tr("download.install_failed"), blocked = false)
        }
    }

    internal fun onInstalled() {
        _state.value = AddonState.Idle
        _installs.value++
    }

    internal fun onInstallFailed(status: Int, message: String) {
        val blocked = status == PackageInstaller.STATUS_FAILURE_BLOCKED ||
            message.contains("protect", ignoreCase = true)
        // The user tapping Cancel on Android's install screen isn't an error worth shouting about.
        _state.value = if (status == PackageInstaller.STATUS_FAILURE_ABORTED) AddonState.Idle
        else AddonState.Failed(message, blocked)
    }

    fun dismiss() { _state.value = AddonState.Idle }

    @androidx.annotation.VisibleForTesting
    internal fun setStateForTest(state: AddonState) { _state.value = state }

    /** App info for the add-on: where Android 13+ hides "Allow restricted settings". */
    fun appInfoIntent(): Intent =
        Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$PACKAGE"))
}
