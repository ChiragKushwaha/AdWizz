package rocks.chirag.adwizz.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import rocks.chirag.adwizz.data.ProtectionPause
import rocks.chirag.adwizz.data.VpnState
import rocks.chirag.adwizz.tr

/** Pause choices in minutes; null = until resumed. */
internal val PAUSE_CHOICES: List<Pair<Int?, String>> = listOf(
    5 to "pause.5m", 15 to "pause.15m", 60 to "pause.1h", null to "pause.forever",
)

/**
 * "Pause blocking" under the main button: for a site or app that breaks with ads blocked.
 * The VPN stays connected, so resuming is instant; a timed pause ends by itself.
 */
@Composable
internal fun PausePanel() {
    val context = LocalContext.current
    val until by VpnState.pausedUntil.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var menu by remember { mutableStateOf(false) }
    // Tick while paused so the panel flips back by itself when the pause ends.
    LaunchedEffect(until) {
        while (until > System.currentTimeMillis()) { now = System.currentTimeMillis(); delay(1_000) }
        now = System.currentTimeMillis()
    }
    val paused = until > now
    Row(
        Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                !paused -> tr("pause.hint")
                until == ProtectionPause.FOREVER -> tr("pause.paused_forever")
                else -> tr("pause.paused_until", "time" to DateFormat.getTimeFormat(context).format(until))
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (paused) FontWeight.Bold else FontWeight.Normal,
            color = if (paused) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).testTag("pauseText"),
        )
        if (paused) {
            TextButton(onClick = { ProtectionPause.resumeBlocking(context) }, modifier = Modifier.testTag("resume")) {
                Text(tr("pause.resume"))
            }
        } else Box {
            TextButton(onClick = { menu = true }, modifier = Modifier.testTag("pause")) { Text(tr("pause.pause")) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                PAUSE_CHOICES.forEach { (minutes, key) ->
                    DropdownMenuItem(text = { Text(tr(key)) }, onClick = {
                        menu = false
                        ProtectionPause.pauseBlocking(context, minutes)
                    })
                }
            }
        }
    }
}
