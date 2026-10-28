package rocks.chirag.adwizz.ui

import rocks.chirag.adwizz.tr
import androidx.compose.runtime.saveable.rememberSaveable
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
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    SectionTitle(tr("settings.updates"))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("AdWizz v${BuildConfig.VERSION_NAME}")
            Text(result ?: tr("settings.updates_from"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(enabled = !checking, onClick = {
            val app = context.applicationContext
            checking = true
            scope.launch {
                val found = withContext(Dispatchers.IO) { Updater.check(app, force = true) }
                result = if (found != null) tr("settings.update_found", "version" to found.versionName)
                else tr("settings.up_to_date")
                checking = false
            }
        }) { Text(if (checking) tr("settings.checking") else tr("settings.check")) }
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
    SectionTitle(tr("settings.stick"))
    PixelPanel(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("settings.always_on"), fontWeight = FontWeight.SemiBold)
            Text(tr("settings.always_on_desc"),
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { open(context, AndroidSettings.ACTION_VPN_SETTINGS) }) { Text("Open VPN settings") }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                if (unrestricted) tr("settings.battery_ok") else tr("settings.battery_opt"),
                fontWeight = FontWeight.SemiBold,
                color = if (unrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Text(oemHint(), style = MaterialTheme.typography.bodySmall)
            Row {
                if (!unrestricted) TextButton(onClick = { requestBatteryExemption(context) }) { Text(tr("settings.allow_background")) }
                TextButton(onClick = { openAppDetails(context) }) { Text(tr("settings.app_settings")) }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("settings.pdns_title"), fontWeight = FontWeight.SemiBold)
            }
            Text(tr("settings.pdns_desc"),
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { open(context, AndroidSettings.ACTION_WIRELESS_SETTINGS) }) { Text("Open network settings") }
        }
    }
}
