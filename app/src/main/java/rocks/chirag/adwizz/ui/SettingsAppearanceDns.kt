package rocks.chirag.adwizz.ui

import rocks.chirag.adwizz.tr
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rocks.chirag.adwizz.data.DnsPresets
import rocks.chirag.adwizz.data.Settings

/** System / Light / Dark; the new theme spreads out from the tapped button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceSection() {
    val themeMode by ThemeState.mode.collectAsStateWithLifecycle()
    val reveal = LocalThemeReveal.current
    SectionTitle(tr("settings.appearance"))
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ThemeMode.entries.forEachIndexed { i, mode ->
            var center by remember { mutableStateOf(Offset.Zero) }
            SegmentedButton(
                selected = themeMode == mode,
                onClick = { switchTheme(reveal, mode, center) },
                modifier = Modifier.testTag("theme_${mode.key}")
                    .onGloballyPositioned { center = it.boundsInRoot().center },
                shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                label = { Text(tr(mode.label)) }
            )
        }
    }
}

/** Which resolver answers the queries AdWizz doesn't block: presets or two custom IPv4s. */
@Composable
internal fun DnsSection() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    var saved by remember { mutableStateOf(settings.primaryDns to settings.secondaryDns) }
    // Half-typed addresses survive rotation.
    var dns1 by rememberSaveable { mutableStateOf(saved.first) }
    var dns2 by rememberSaveable { mutableStateOf(saved.second) }
    fun save(primary: String, secondary: String) {
        settings.primaryDns = primary; settings.secondaryDns = secondary
        saved = primary to secondary
        reload(context)
    }

    SectionTitle(tr("settings.upstream_dns"))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DnsPresets.ALL.forEach { p ->
            FilterChip(
                selected = dns1 == p.primary,
                onClick = { dns1 = p.primary; dns2 = p.secondary; save(p.primary, p.secondary) },
                label = { Text(p.name, fontSize = 12.sp) }
            )
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(dns1, { dns1 = it.trim() }, label = { Text(tr("settings.dns_primary")) }, singleLine = true, modifier = Modifier.weight(1f))
        OutlinedTextField(dns2, { dns2 = it.trim() }, label = { Text(tr("settings.dns_secondary")) }, singleLine = true, modifier = Modifier.weight(1f))
    }
    if (dns1 != saved.first || dns2 != saved.second) {
        TextButton(onClick = {
            if (isIpv4(dns1) && (dns2.isEmpty() || isIpv4(dns2))) save(dns1, dns2)
            else toast(context, tr("settings.dns_invalid"))
        }) { Text(tr("settings.dns_save")) }
    }
}
