package rocks.chirag.adshield.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rocks.chirag.adshield.data.*

data class AppEntry(val pkg: String, val label: String)

/** Launchable apps other than AdShield itself, sorted by name. Slow — call off the main thread. */
fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .map { it.activityInfo.applicationInfo }
        .distinctBy { it.packageName }
        .filter { it.packageName != context.packageName }
        .map { AppEntry(it.packageName, pm.getApplicationLabel(it).toString()) }
        .sortedBy { it.label.lowercase() }
}

/** Search filter for the picker: matches app name or package; selected apps listed first. */
fun filterApps(apps: List<AppEntry>, query: String, chosen: Set<String>): List<AppEntry> =
    apps.filter { query.isBlank() || it.label.contains(query.trim(), true) || it.pkg.contains(query.trim(), true) }
        .sortedByDescending { it.pkg in chosen }

@Composable
internal fun AppPickerDialog(initial: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var chosen by remember { mutableStateOf(initial) }
    LaunchedEffect(Unit) {
        val app = context.applicationContext
        apps = withContext(Dispatchers.IO) { loadApps(app) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exclude apps") },
        text = { AppPickerContent(apps, chosen, onChange = { chosen = it }) },
        confirmButton = { TextButton(onClick = { onSave(chosen) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Body of the app picker: search box + checkable list. [apps] null means still loading. */
@Composable
fun AppPickerContent(apps: List<AppEntry>?, chosen: Set<String>, onChange: (Set<String>) -> Unit) {
    var query by remember { mutableStateOf("") }
    Column {
        OutlinedTextField(
            query, { query = it }, placeholder = { Text("Search apps") },
            singleLine = true, modifier = Modifier.fillMaxWidth().testTag("appSearch")
        )
        Spacer(Modifier.height(8.dp))
        if (apps == null) {
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text("Loading apps…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        val shown = remember(apps, query, chosen) { filterApps(apps, query, chosen) }
        if (shown.isEmpty()) {
            Text(
                if (apps.isEmpty()) "No apps found" else "No apps match \"$query\"",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        }
        LazyColumn(Modifier.heightIn(max = 400.dp).testTag("appList")) {
            items(shown, key = { it.pkg }) { app ->
                val on = app.pkg in chosen
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { onChange(if (on) chosen - app.pkg else chosen + app.pkg) }
                        .padding(vertical = 4.dp)
                        .testTag("app_${app.pkg}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            app.pkg, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
