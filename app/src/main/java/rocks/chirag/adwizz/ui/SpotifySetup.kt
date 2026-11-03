package rocks.chirag.adwizz.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rocks.chirag.adwizz.tr

private const val SPOTIFY = "com.spotify.music"

/**
 * Spotify only tells other apps what it's playing when "Device broadcast status" is on in its
 * settings, and muting its ads depends on that. Shown when "Mute Spotify ads" is switched on.
 */
@Composable
internal fun SpotifySetupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val connected = rememberSpotifyConnected()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("spotifySetup"),
        title = { Text(tr("spotify.setup_title")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tr("spotify.setup_body"))
                Text(tr("spotify.step1"))
                Text(tr("spotify.step2"))
                Text(tr("spotify.step3"))
                SpotifyStatus(connected)
            }
        },
        confirmButton = { TextButton(onClick = { openSpotifySettings(context) }) { Text(tr("spotify.open_settings")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("common.done")) } },
    )
}

/** "Spotify is connected" once a broadcast arrives, otherwise a hint to play a song. */
@Composable
internal fun SpotifyStatus(connected: Boolean) {
    Text(
        if (connected) tr("spotify.connected") else tr("spotify.waiting"),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = if (connected) FontWeight.Bold else FontWeight.Normal,
        color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("spotifyStatus"),
    )
}

/** Listens for Spotify's playback broadcasts while on screen: proof the setting is on. */
@Composable
internal fun rememberSpotifyConnected(): Boolean {
    val context = LocalContext.current
    var connected by remember { mutableStateOf(SpotifyLink.heard) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { SpotifyLink.heard = true; connected = true }
        }
        val filter = IntentFilter().apply {
            addAction("com.spotify.music.metadatachanged"); addAction("com.spotify.music.playbackstatechanged")
        }
        // Spotify is another app: its broadcasts need an exported receiver.
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, filter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return connected
}

/** Remembers for this run that Spotify broadcasts (so the dialog doesn't nag after it's set up). */
internal object SpotifyLink { @Volatile var heard = false }

/** Opens Spotify's own settings screen, or Spotify itself if that link stops working. */
internal fun openSpotifySettings(context: Context) {
    val settings = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:internal:preferences")).setPackage(SPOTIFY)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val ok = runCatching { context.startActivity(settings) }.isSuccess
    if (ok) return
    val launch = context.packageManager.getLaunchIntentForPackage(SPOTIFY)
    if (launch != null) context.startActivity(launch) else toast(context, tr("addon.no_spotify"))
}
