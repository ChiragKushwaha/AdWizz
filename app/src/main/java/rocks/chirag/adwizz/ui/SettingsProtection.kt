package rocks.chirag.adwizz.ui

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
    SectionTitle("Protection")
    SettingSwitch(settings, Settings::catchHardcodedDns, "Block DNS bypass",
        "Catches apps that hard-code Google/Cloudflare/etc. DNS or use DNS-over-HTTPS to them.") { restartVpnIfRunning(context) }
    SettingSwitch(settings, Settings::blockTrackers, "Block trackers & analytics scripts",
        "Analytics, tracking pixels, fingerprinting scripts, disguised first-party trackers " +
            "and phone-maker telemetry (Samsung, Xiaomi, TikTok…).") { reload(context) }
    SettingSwitch(settings, Settings::blockGenerated, "Block gibberish ad domains",
        "Catches brand-new ad domains like pgjlctmswgnwf.com before any list has them. " +
            "If a site breaks, allow it from the Activity tab.") { reload(context) }
    SettingSwitch(settings, Settings::blockMalicious, "Block malicious & crypto-mining scripts",
        "Phishing and scam sites, and scripts that mine crypto-currency in your browser.") { reload(context) }
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
    var picking by remember { mutableStateOf(false) }

    SectionTitle("Excluded apps (VPN hidden from them)")
    Text(
        "Some apps (banking, streaming, games) refuse to work when a VPN is on. Excluded apps " +
            "skip AdWizz completely: they won't see the VPN, but their ads aren't blocked either.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(onClick = { picking = true }, modifier = Modifier.padding(top = 8.dp)) {
        Text(if (excluded.isEmpty()) "Choose apps" else "${excluded.size} app(s) excluded — edit")
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
