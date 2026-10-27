package rocks.chirag.adwizz.ui

import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rocks.chirag.adwizz.BuildConfig
import rocks.chirag.adwizz.data.Updater

/** Current version and a manual "Check for updates" (the app also checks daily by itself). */
@Composable
internal fun UpdatesSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    SectionTitle("Updates")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("AdWizz v${BuildConfig.VERSION_NAME}")
            Text(result ?: "Updates come from GitHub Releases", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(enabled = !checking, onClick = {
            val app = context.applicationContext
            checking = true
            scope.launch {
                val found = withContext(Dispatchers.IO) { Updater.check(app, force = true) }
                result = if (found != null) "v${found.versionName} is available — see the top of the screen"
                else "You're on the latest version"
                checking = false
            }
        }) { Text(if (checking) "Checking…" else "Check for updates") }
    }
}

/** How to keep protection running: Always-on VPN, battery, and the Private DNS trap. */
@Composable
internal fun KeepAliveSection() {
    val context = LocalContext.current
    var unrestricted by remember { mutableStateOf(isBatteryUnrestricted(context)) }
    LifecycleResumeEffect(Unit) {
        unrestricted = isBatteryUnrestricted(context)
        onPauseOrDispose { }
    }
    SectionTitle("Make it stick")
    PixelPanel(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp)) {
            Text("Always-on VPN", fontWeight = FontWeight.SemiBold)
            Text("Turn on Always-on for AdWizz so protection survives reboots and the system restarts it if it's killed.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                // The VPN settings screen exists from Android 7; older phones get Network settings.
                open(context, if (Build.VERSION.SDK_INT >= 24) AndroidSettings.ACTION_VPN_SETTINGS else AndroidSettings.ACTION_WIRELESS_SETTINGS)
            }) { Text("Open VPN settings") }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                if (unrestricted) "Battery: unrestricted ✓" else "Battery: optimised (may be stopped)",
                fontWeight = FontWeight.SemiBold,
                color = if (unrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(oemHint(), style = MaterialTheme.typography.bodySmall)
            Row {
                if (!unrestricted) TextButton(onClick = { requestBatteryExemption(context) }) { Text("Allow background") }
                TextButton(onClick = { openAppDetails(context) }) { Text("App settings") }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Private DNS must be Off or Automatic", fontWeight = FontWeight.SemiBold)
            }
            Text("If Private DNS is set to a specific hostname (e.g. dns.google), Android sends DNS over TLS directly and skips AdWizz.",
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { open(context, AndroidSettings.ACTION_WIRELESS_SETTINGS) }) { Text("Open network settings") }
        }
    }
}
