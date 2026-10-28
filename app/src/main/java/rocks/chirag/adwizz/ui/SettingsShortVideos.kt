package rocks.chirag.adwizz.ui

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

    SectionTitle("Focus")
    SwitchRow(
        "Block Reels & Shorts",
        "Leaves Instagram Reels and YouTube Shorts as soon as they open, in the apps and in " +
            "browsers like Chrome, and closes short-video apps (TikTok, Moj, Josh, Likee and more). " +
            "With protection on, TikTok and similar sites are blocked in every app too.",
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
            title = { Text("Install the Reels Blocker add-on?") },
            text = {
                Text(
                    "Blocking Reels and Shorts uses Android's Accessibility service, so it comes as a " +
                        "small separate app, \"AdWizz Reels Blocker\" (about 50 KB). AdWizz downloads it " +
                        "from the same place as AdWizz itself and checks it's signed by AdWizz.\n\n" +
                        "Google Play Protect may block it, because it can use Accessibility. If it does, " +
                        "AdWizz shows what to do."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    setOn(true)
                    startAddonInstall(context)
                }) { Text("Install") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Not now") } },
        )
        FocusDialog.ACCESSIBILITY -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Allow the Reels Blocker to see which screen is open?") },
            text = {
                Text(
                    "To spot Reels and Shorts, the add-on uses Android's Accessibility service to check " +
                        "which screen Instagram, YouTube and short-video apps are showing, and which address " +
                        "your browser has open, then presses Back for you.\n\nIt only looks at those apps " +
                        "and browsers, doesn't read your messages, pages or what you type, and nothing " +
                        "leaves your phone.\n\nAndroid needs a few taps to allow it: AdWizz shows each step."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    setOn(true)   // the setup guide appears under the switch
                }) { Text("Show me how") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Not now") } },
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
        state is AddonState.Downloading -> note("Downloading the Reels Blocker add-on… ${(state.progress * 100).toInt()}%", error = false)
        state is AddonState.Installing -> note("Installing the Reels Blocker add-on…", error = false)
        state is AddonState.Failed && state.blocked -> {
            note(
                "Google Play Protect blocked the add-on because it can use Accessibility. To install it " +
                    "anyway: open the Play Store, tap your profile picture → Play Protect → ⚙ settings, turn " +
                    "off \"Scan apps with Play Protect\", tap Try again here, then turn scanning back on."
            )
            TextButton(onClick = { startAddonInstall(context) }) { Text("Try again") }
        }
        state is AddonState.Failed -> {
            note("Couldn't install the add-on: ${state.message}")
            TextButton(onClick = { startAddonInstall(context) }) { Text("Try again") }
        }
        !installed -> {
            note("Needs the Reels Blocker add-on.")
            TextButton(onClick = { startAddonInstall(context) }) { Text("Install add-on") }
        }
        !serviceOn -> {
            note("Needs one more step: turn on \"AdWizz Reels Blocker\" in Accessibility settings.")
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
