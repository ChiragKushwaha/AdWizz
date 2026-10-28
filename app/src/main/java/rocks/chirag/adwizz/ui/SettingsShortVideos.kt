package rocks.chirag.adwizz.ui

import rocks.chirag.adwizz.tr
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import rocks.chirag.adwizz.data.AddonState
import rocks.chirag.adwizz.data.FocusAddon
import rocks.chirag.adwizz.data.Settings
import rocks.chirag.adwizz.data.Updater
import rocks.chirag.adwizz.focus.FocusStateProvider

private enum class FocusDialog { ADDON, ACCESSIBILITY }

/**
 * "Block Reels & Shorts". Leaving Reels and Shorts needs an Accessibility service, which lives in
 * the separate "AdWizz Reels Blocker" add-on (see FocusAddon): turning the switch on installs the
 * add-on, then asks the user to allow it in Accessibility settings.
 */
@Composable
internal fun ShortVideoSection() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val addonState by FocusAddon.state.collectAsState()
    val installs by FocusAddon.installs.collectAsState()
    var on by remember { mutableStateOf(settings.blockShortVideos) }
    var installed by remember { mutableStateOf(FocusAddon.isInstalled(context)) }
    var serviceOn by remember { mutableStateOf(FocusAddon.isServiceEnabled(context)) }
    var dialog by rememberSaveable { mutableStateOf<FocusDialog?>(null) }

    fun refresh() {
        installed = FocusAddon.isInstalled(context)
        serviceOn = FocusAddon.isServiceEnabled(context)
    }
    fun setOn(want: Boolean) {
        on = want
        settings.blockShortVideos = want
        FocusStateProvider.notifyChanged(context)
        reload(context)
    }
    LifecycleResumeEffect(Unit) {
        refresh()
        onPauseOrDispose { }
    }
    // Right after the add-on installs, go straight on to the Accessibility step.
    LaunchedEffect(installs) {
        refresh()
        if (installs > 0 && on && installed && !serviceOn) dialog = FocusDialog.ACCESSIBILITY
    }

    SectionTitle(tr("reels.section"))
    SwitchRow(
        tr("reels.title"),
        tr("reels.desc"),
        on,
    ) { want ->
        when {
            !want -> setOn(false)
            !installed -> dialog = FocusDialog.ADDON
            !serviceOn -> dialog = FocusDialog.ACCESSIBILITY
            else -> setOn(true)
        }
    }
    if (on) {
        FocusStatus(addonState, installed, serviceOn, context)
    }

    when (dialog) {
        FocusDialog.ADDON -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(tr("reels.install_title")) },
            text = {
                Text(
                    tr("reels.install_body")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    setOn(true)
                    startAddonInstall(context)
                }) { Text(tr("common.install")) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(tr("common.not_now")) } },
        )
        FocusDialog.ACCESSIBILITY -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(tr("reels.a11y_title")) },
            text = {
                Text(
                    tr("reels.a11y_body")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    setOn(true)   // the setup guide appears under the switch
                }) { Text(tr("reels.show_how")) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text(tr("common.not_now")) } },
        )
        null -> Unit
    }
}

/** What's still needed for the switch to work, with a button for the next step. */
@Composable
private fun FocusStatus(state: AddonState, installed: Boolean, serviceOn: Boolean, context: Context) {
    @Composable fun note(text: String, error: Boolean = true) = Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when {
        state is AddonState.Downloading -> note(tr("reels.downloading", "percent" to (state.progress * 100).toInt()), error = false)
        state is AddonState.Installing -> note(tr("reels.installing"), error = false)
        state is AddonState.Failed && state.blocked -> {
            note(
                tr("reels.blocked")
            )
            TextButton(onClick = { startAddonInstall(context) }) { Text(tr("common.try_again")) }
        }
        state is AddonState.Failed -> {
            note(tr("reels.failed", "error" to state.message))
            TextButton(onClick = { startAddonInstall(context) }) { Text(tr("common.try_again")) }
        }
        !installed -> {
            note(tr("reels.needs_addon"))
            TextButton(onClick = { startAddonInstall(context) }) { Text(tr("reels.install_addon")) }
        }
        !serviceOn -> {
            note(tr("reels.needs_a11y"))
            ReelsSetupGuide(
                onOpenAccessibility = { open(context, AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS) },
                onOpenAddonInfo = { runCatching { context.startActivity(FocusAddon.appInfoIntent()) } },
            )
        }
    }
}

/**
 * Downloads and installs the add-on. The first time, Android needs the user to allow AdWizz to
 * install apps; they come back and tap "Install add-on".
 */
private fun startAddonInstall(context: Context) {
    val app = context.applicationContext
    if (!Updater.canInstall(app)) {
        runCatching {
            context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
        }
        return
    }
    FocusAddon.dismiss()
    Thread { FocusAddon.install(app) }.start()
}
