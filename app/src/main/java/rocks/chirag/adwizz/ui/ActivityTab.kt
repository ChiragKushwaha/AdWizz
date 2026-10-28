package rocks.chirag.adwizz.ui

import rocks.chirag.adwizz.tr
import androidx.compose.runtime.saveable.rememberSaveable
import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rocks.chirag.adwizz.data.*

internal val Danger = Color(0xFFDC2626)

// ---------------- Activity ----------------

@Composable
internal fun ActivityTab() {
    val log by VpnState.log.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // The open "allow / block this domain" dialog survives rotation.
    var selected by rememberSaveable(stateSaver = QueryLogSaver) { mutableStateOf<QueryLog?>(null) }

    if (log.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                tr("activity.empty"),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize().testTag("activityList")) {
        items(log) { entry ->
            ListItem(
                modifier = Modifier.clickable { selected = entry },
                headlineContent = {
                    Text(entry.domain, fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(DateUtils.getRelativeTimeSpanString(entry.time).toString(), fontSize = 11.sp)
                },
                trailingContent = {
                    Text(
                        if (entry.blocked) tr("activity.blocked") else tr("activity.allowed"),
                        color = if (entry.blocked) Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp, fontWeight = if (entry.blocked) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }

    selected?.let { entry ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(entry.domain, fontFamily = FontFamily.Monospace, fontSize = 15.sp) },
            text = {
                Text(
                    if (entry.blocked) tr("activity.blocked_hint")
                    else tr("activity.allowed_hint")
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val s = Settings(context)
                    if (entry.blocked) {
                        s.allowlist = s.allowlist + entry.domain
                        s.customBlocklist = s.customBlocklist - entry.domain
                    } else {
                        s.customBlocklist = s.customBlocklist + entry.domain
                        s.allowlist = s.allowlist - entry.domain
                    }
                    reload(context)
                    selected = null
                }) { Text(if (entry.blocked) tr("common.allow") else tr("common.block")) }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text(tr("common.cancel")) } }
        )
    }
}

/** Saves a [QueryLog] (three plain values) across rotation and process death. */
internal val QueryLogSaver = androidx.compose.runtime.saveable.listSaver<QueryLog?, Any>(
    save = { q -> if (q == null) emptyList() else listOf(q.domain, q.blocked, q.time) },
    restore = { l -> if (l.size == 3) QueryLog(l[0] as String, l[1] as Boolean, l[2] as Long) else null },
)
