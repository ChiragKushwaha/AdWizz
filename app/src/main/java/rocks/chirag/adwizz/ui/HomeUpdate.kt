package rocks.chirag.adwizz.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rocks.chirag.adwizz.data.UpdateState
import rocks.chirag.adwizz.data.Updater
import rocks.chirag.adwizz.data.info

/**
 * Shown when a newer AdWizz is on GitHub Releases. It's downloaded and verified in the background
 * as soon as it's found, so "Install" is usually instant.
 */
@Composable
internal fun UpdatePanel() {
    val context = LocalContext.current
    val state by Updater.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        val app = context.applicationContext
        withContext(Dispatchers.IO) {
            val info = Updater.check(app)
            // Fetch it now so "Install" is instant (only when merely known about, not mid-download).
            if (info != null && Updater.autoDownload && Updater.state.value is UpdateState.Available) Updater.prepare(app, info)
        }
    }
    val info = state.info ?: return
    PixelPanel(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 2.dp).testTag("updatePanel"),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("New spell book: v${info.versionName}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
            if (info.notes.isNotBlank()) {
                Text(info.notes, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(8.dp))
            when (val st = state) {
                is UpdateState.Downloading -> LinearProgressIndicator(
                    progress = { st.progress }, modifier = Modifier.fillMaxWidth().height(8.dp), strokeCap = StrokeCap.Butt,
                )
                is UpdateState.Installing -> Text("Installing… AdWizz restarts by itself", style = MaterialTheme.typography.bodySmall)
                else -> UpdateActions(st)
            }
        }
    }
}

@Composable
private fun UpdateActions(state: UpdateState) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (state is UpdateState.Failed) {
            Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        } else Spacer(Modifier.weight(1f))
        TextButton(onClick = { Updater.dismiss() }) { Text("Later") }
        PixelButton(
            when (state) { is UpdateState.Failed -> "Retry"; is UpdateState.Ready -> "Install"; else -> "Update" },
            onClick = { startUpdate(context) },
            modifier = Modifier.testTag("updateButton"),
        )
    }
}

/**
 * Installs the pending update. The first time, Android needs the user to allow AdWizz to install
 * apps (a one-time switch no app can skip); after that it's a single tap.
 */
internal fun startUpdate(context: Context) {
    val info = Updater.state.value.info ?: return
    val app = context.applicationContext
    if (!Updater.canInstall(app)) {
        runCatching {
            context.startActivity(Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
        }
        return
    }
    Thread { Updater.install(app, info) }.start()
}
