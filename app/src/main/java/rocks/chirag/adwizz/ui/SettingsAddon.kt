package rocks.chirag.adwizz.ui

import android.content.Context
import android.provider.Settings as AndroidSettings
import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rocks.chirag.adwizz.data.*
import rocks.chirag.adwizz.focus.FocusStateProvider
import rocks.chirag.adwizz.tr

/**
 * The AdWizz add-on (the Accessibility service, see FocusAddon): install, update, uninstall,
 * pause or switch it off, plus the ad features that need it: skipping YouTube ads and muting
 * Spotify ads. Those ads come from the same servers as the videos and songs, so DNS can't stop them.
 */
@Composable
internal fun AddonSection() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val state by FocusAddon.state.collectAsState()
    val installs by FocusAddon.installs.collectAsState()
    val latest by FocusAddon.latest.collectAsState()
    var installed by remember { mutableStateOf(FocusAddon.installedVersionName(context)) }
    var serviceOn by remember { mutableStateOf(FocusAddon.isServiceEnabled(context)) }
    var youtube by remember { mutableStateOf(settings.skipYoutubeAds) }
    var spotify by remember { mutableStateOf(settings.muteSpotifyAds) }
    var spotifySetup by rememberSaveable { mutableStateOf(false) }

    fun refresh() {
        installed = FocusAddon.installedVersionName(context)
        serviceOn = FocusAddon.isServiceEnabled(context)
    }
    LifecycleResumeEffect(Unit) { refresh(); onPauseOrDispose { } }
    LaunchedEffect(installs) { refresh() }
    LaunchedEffect(Unit) { withContext(Dispatchers.IO) { FocusAddon.checkLatest() } }

    fun set(feature: (Boolean) -> Unit, on: Boolean) {
        feature(on)
        FocusStateProvider.notifyChanged(context)
        if (on && installed == null) startAddonInstall(context)
    }

    SectionTitle(tr("addon.section"))
    Note(tr("addon.intro"))
    SwitchRow(tr("addon.youtube"), tr("addon.youtube_desc"), youtube) { on ->
        youtube = on; set({ settings.skipYoutubeAds = it }, on)
    }
    SwitchRow(tr("addon.spotify"), tr("addon.spotify_desc"), spotify) { on ->
        spotify = on; set({ settings.muteSpotifyAds = it }, on)
        // Muting only works once Spotify shares what it plays: show how, unless it already does.
        if (on && !SpotifyLink.heard) spotifySetup = true
    }
    if (spotify) {
        SpotifyStatus(rememberSpotifyConnected())
        TextButton(onClick = { spotifySetup = true }, modifier = Modifier.testTag("spotifyHow")) { Text(tr("spotify.how")) }
    }
    if (spotifySetup) SpotifySetupDialog { spotifySetup = false }

    AddonStatus(state, installed, latest, serviceOn, context)
    if (installed != null && (youtube || spotify || settings.blockShortVideos) && !serviceOn) {
        ReelsSetupGuide(
            onOpenAccessibility = { open(context, AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS) },
            onOpenAddonInfo = { runCatching { context.startActivity(FocusAddon.appInfoIntent()) } },
        )
    }
    if (installed != null && serviceOn) AddonPauseRow(context)
}

/** "Installed · v1.1.0" with Install / Update / Uninstall, or the download's progress. */
@Composable
private fun AddonStatus(state: AddonState, installed: String?, latest: AddonInfo?, serviceOn: Boolean, context: Context) {
    val busy = state is AddonState.Downloading || state is AddonState.Installing
    Text(
        when {
            state is AddonState.Downloading -> tr("reels.downloading", "percent" to (state.progress * 100).toInt())
            state is AddonState.Installing -> tr("reels.installing")
            state is AddonState.Failed -> tr("reels.failed", "error" to state.message)
            installed == null -> tr("addon.not_installed")
            !serviceOn -> tr("addon.installed_off", "version" to installed)
            else -> tr("addon.installed_on", "version" to installed)
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (state is AddonState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp).testTag("addonStatus"),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            installed == null -> TextButton(enabled = !busy, onClick = { startAddonInstall(context) }) { Text(tr("common.install")) }
            FocusAddon.updateAvailable(context) -> TextButton(enabled = !busy, onClick = { startAddonInstall(context) }) {
                Text(tr("addon.update"))
            }
        }
        if (installed != null) {
            TextButton(onClick = { runCatching { context.startActivity(FocusAddon.uninstallIntent()) } }, modifier = Modifier.testTag("addonUninstall")) {
                Text(tr("addon.uninstall"))
            }
            TextButton(onClick = { open(context, AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS) }) { Text(tr("guide.open_a11y")) }
        }
    }
}

/** Pause for a while (it stops watching apps), or switch it off for apps that refuse Accessibility. */
@Composable
private fun AddonPauseRow(context: Context) {
    var until by remember { mutableLongStateOf(Settings(context).addonPausedUntil) }
    var menu by remember { mutableStateOf(false) }
    val paused = until > System.currentTimeMillis()
    Text(
        when {
            !paused -> tr("addon.pause_hint")
            until == ProtectionPause.FOREVER -> tr("pause.paused_forever")
            else -> tr("pause.paused_until", "time" to DateFormat.getTimeFormat(context).format(until))
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
    Row {
        if (paused) TextButton(onClick = { ProtectionPause.resumeAddon(context); until = 0 }) { Text(tr("pause.resume")) }
        else Box {
            TextButton(onClick = { menu = true }, modifier = Modifier.testTag("addonPause")) { Text(tr("addon.pause")) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                PAUSE_CHOICES.forEach { (minutes, key) ->
                    DropdownMenuItem(text = { Text(tr(key)) }, onClick = {
                        menu = false
                        ProtectionPause.pauseAddon(context, minutes)
                        until = Settings(context).addonPausedUntil
                    })
                }
            }
        }
        TextButton(onClick = { ProtectionPause.turnOffAddon(context); toast(context, tr("addon.turned_off")) }) {
            Text(tr("addon.turn_off"))
        }
    }
}
