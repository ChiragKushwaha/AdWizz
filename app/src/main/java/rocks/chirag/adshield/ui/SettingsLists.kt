package rocks.chirag.adshield.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rocks.chirag.adshield.data.BlocklistRepository
import rocks.chirag.adshield.data.Settings
import rocks.chirag.adshield.data.Sources
import rocks.chirag.adshield.data.VpnState

/** Ad lists picked one by one, plus a manual "Update lists" button. */
@Composable
internal fun BlocklistSection() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()
    val updating by VpnState.updatingLists.collectAsStateWithLifecycle()
    var sources by remember { mutableStateOf(settings.enabledSources) }
    var lastUpdate by remember { mutableLongStateOf(settings.lastListUpdate) }

    SectionTitle("Blocklists")
    Sources.ADS.forEach { src ->
        SwitchRow(src.name, src.description, src.id in sources) { on ->
            sources = if (on) sources + src.id else sources - src.id
            settings.enabledSources = sources
            reload(context)   // the running VPN also fetches any list not on disk yet
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Button(enabled = !updating, onClick = {
            // Application context: a download can outlive this screen (e.g. rotation).
            val app = context.applicationContext
            scope.launch {
                val msg = withContext(Dispatchers.IO) { BlocklistRepository(app).downloadAll() }
                lastUpdate = settings.lastListUpdate
                reload(app)
                toast(app, msg)
            }
        }) {
            if (updating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Update lists")
        }
        Spacer(Modifier.width(12.dp))
        Text(
            if (lastUpdate == 0L) "Never updated" else "Updated ${DateUtils.getRelativeTimeSpanString(lastUpdate)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun AllowlistSection() {
    val settings = rememberSettings()
    DomainListSection("Allowlist (never blocked)", "example.com",
        read = { settings.allowlist }, write = { settings.allowlist = it })
}

@Composable
internal fun CustomBlocklistSection() {
    val settings = rememberSettings()
    DomainListSection("My blocked domains", "ads.example.com",
        read = { settings.customBlocklist }, write = { settings.customBlocklist = it })
}

/** A titled, editable list of domains: add box on top, one removable row per domain. */
@Composable
private fun DomainListSection(title: String, hint: String, read: () -> Set<String>, write: (Set<String>) -> Unit) {
    val context = LocalContext.current
    var domains by remember { mutableStateOf(read().sorted()) }
    fun update(new: Set<String>) {
        write(new)
        domains = new.sorted()
        reload(context)
    }
    SectionTitle(title)
    DomainAdder(hint) { update(read() + it) }
    domains.forEach { d -> key(d) { DomainRow(d) { update(read() - d) } } }
}

@Composable
private fun rememberSettings(): Settings {
    val context = LocalContext.current
    return remember { Settings(context) }
}
