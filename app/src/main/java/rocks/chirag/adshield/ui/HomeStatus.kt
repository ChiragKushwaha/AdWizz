package rocks.chirag.adshield.ui

import android.provider.Settings as AndroidSettings
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import rocks.chirag.adshield.data.*

/** Status line plus the big pixel button that starts (casts the spell) or stops protection. */
@Composable
internal fun StatusPanel(status: Status, onToggle: () -> Unit) {
    val on = status == Status.RUNNING
    PixelPanel(Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(status, label = "statusText", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) { st ->
                    Text(
                        when (st) {
                            Status.RUNNING -> "Protected"
                            Status.STARTING -> "Starting…"
                            Status.ERROR -> "Couldn't start"
                            Status.STOPPED -> "Not protected"
                        },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = if (st == Status.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("statusText")
                    )
                }
                Text(
                    if (on) "Ads and trackers are blocked in every app" else "Tap to block ads across your phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            PixelButton(
                text = when (status) {
                    Status.RUNNING -> "Stop"
                    Status.STARTING -> "Casting…"
                    else -> "Cast spell"
                },
                onClick = onToggle,
                modifier = Modifier.testTag("power")
                    .semantics { contentDescription = if (on) "Turn protection off" else "Turn protection on" },
            )
        }
    }
}

@Composable
internal fun PrivateDnsWarning() {
    val context = LocalContext.current
    var strict by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        strict = isPrivateDnsStrict(context)
        onPauseOrDispose { }
    }
    if (!strict) return
    PixelPanel(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 4.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Private DNS is bypassing AdWizz", fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer)
            Text("Set Private DNS to Off or Automatic, otherwise ads won't be blocked.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = { open(context, AndroidSettings.ACTION_WIRELESS_SETTINGS) }) { Text("Fix it") }
        }
    }
}
