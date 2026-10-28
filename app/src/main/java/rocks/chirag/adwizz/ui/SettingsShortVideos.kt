package rocks.chirag.adwizz.ui

import android.provider.Settings as AndroidSettings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import rocks.chirag.adwizz.data.Settings
import rocks.chirag.adwizz.focus.ShortVideoBlockerService

/** "Block Reels & Shorts": a setting plus an accessibility service the user has to allow. */
@Composable
internal fun ShortVideoSection() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    var on by remember { mutableStateOf(settings.blockShortVideos) }
    var serviceOn by remember { mutableStateOf(ShortVideoBlockerService.isEnabled(context)) }
    var explaining by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        serviceOn = ShortVideoBlockerService.isEnabled(context)
        onPauseOrDispose { }
    }

    SectionTitle("Focus")
    SwitchRow(
        "Block Reels & Shorts",
        "Leaves Instagram Reels and YouTube Shorts as soon as they open, and closes " +
            "short-video apps (TikTok, Moj, Josh, Chingari). Works with or without the VPN.",
        on,
    ) { want ->
        if (want && !serviceOn) {
            explaining = true
        } else {
            on = want
            settings.blockShortVideos = want
        }
    }
    if (on && !serviceOn) {
        Text(
            "Needs one more step: allow \"AdWizz: block Reels & Shorts\" in Accessibility settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = { open(context, AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS) }) {
            Text("Open Accessibility settings")
        }
    }

    if (explaining) {
        AlertDialog(
            onDismissRequest = { explaining = false },
            title = { Text("Allow AdWizz to see which screen is open?") },
            text = {
                Text(
                    "To spot Reels and Shorts, AdWizz uses Android's Accessibility service to check " +
                        "which screen Instagram, YouTube and short-video apps are showing, then presses " +
                        "Back for you.\n\nIt only looks at those apps, doesn't read your messages or what " +
                        "you type, and nothing leaves your phone.\n\nOn the next screen, tap " +
                        "\"AdWizz: block Reels & Shorts\" and turn it on."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explaining = false
                    on = true
                    settings.blockShortVideos = true
                    open(context, AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { explaining = false }) { Text("Not now") } },
        )
    }
}
