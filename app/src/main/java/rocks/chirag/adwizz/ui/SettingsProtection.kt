package rocks.chirag.adwizz.ui

import rocks.chirag.adwizz.tr
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import rocks.chirag.adwizz.data.Settings
import kotlin.reflect.KMutableProperty1

/** The protection switches. Bypass needs a tunnel rebuild; the others just reload the lists. */
@Composable
internal fun ProtectionSection() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    SectionTitle(tr("settings.protection"))
    SettingSwitch(settings, Settings::catchHardcodedDns, tr("settings.bypass"),
        tr("settings.bypass_desc")) { restartVpnIfRunning(context) }
    SettingSwitch(settings, Settings::blockTrackers, tr("settings.trackers"),
        tr("settings.trackers_desc")) { reload(context) }
    SettingSwitch(settings, Settings::blockGenerated, tr("settings.generated"),
        tr("settings.generated_desc")) { reload(context) }
    SettingSwitch(settings, Settings::blockMalicious, tr("settings.malicious"),
        tr("settings.malicious_desc")) { reload(context) }
}

/** A [SwitchRow] bound to one boolean setting; [onChanged] runs after it's saved. */
@Composable
private fun SettingSwitch(
    settings: Settings,
    property: KMutableProperty1<Settings, Boolean>,
    title: String,
    subtitle: String,
    onChanged: () -> Unit,
) {
    var on by remember { mutableStateOf(property.get(settings)) }
    SwitchRow(title, subtitle, on) {
        on = it
        property.set(settings, it)
        onChanged()
    }
}

/** Apps that skip the VPN entirely (banking/streaming apps that refuse to run with one). */
@Composable
internal fun ExcludedAppsSection() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    var excluded by remember { mutableStateOf(settings.excludedApps) }
    var picking by rememberSaveable { mutableStateOf(false) }

    SectionTitle(tr("settings.excluded"))
    Text(
        tr("settings.excluded_desc"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(onClick = { picking = true }, modifier = Modifier.padding(top = 8.dp)) {
        Text(if (excluded.isEmpty()) tr("settings.choose_apps") else tr("settings.apps_excluded", "count" to excluded.size))
    }
    if (picking) {
        AppPickerDialog(initial = excluded, onDismiss = { picking = false }, onSave = { chosen ->
            picking = false
            excluded = chosen
            settings.excludedApps = chosen
            restartVpnIfRunning(context)   // excluded apps are part of the tunnel setup
        })
    }
}
