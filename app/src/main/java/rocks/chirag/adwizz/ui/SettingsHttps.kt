package rocks.chirag.adwizz.ui

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings as AndroidSettings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rocks.chirag.adwizz.data.Settings
import rocks.chirag.adwizz.https.CertAuthority
import rocks.chirag.adwizz.tr

/**
 * "Decrypt & filter HTTPS": off by default. Browsers then send their traffic through AdWizz,
 * which blocks ad URLs and hides ads on pages. Needs AdWizz's certificate installed as trusted,
 * which only the user can do, in Android's settings. Apps ignore user certificates, so they're
 * never decrypted.
 */
@Composable
internal fun HttpsSection() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()
    var on by remember { mutableStateOf(settings.httpsFiltering) }
    var trusted by remember { mutableStateOf<Boolean?>(null) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val supported = Build.VERSION.SDK_INT >= 29

    LifecycleResumeEffect(on) {
        val job = if (on && supported) scope.launch {
            trusted = withContext(Dispatchers.IO) { runCatching { CertAuthority(context.applicationContext).isTrusted() }.getOrDefault(false) }
        } else null
        onPauseOrDispose { job?.cancel() }
    }

    SectionTitle(tr("https.title"))
    SwitchRow(tr("https.switch"), if (supported) tr("https.desc") else tr("https.needs_android10"), on && supported) { want ->
        when {
            !supported -> Unit
            want -> confirm = true
            else -> { on = false; settings.httpsFiltering = false; restartVpnIfRunning(context) }
        }
    }
    if (on && supported) {
        when (trusted) {
            true -> Text(tr("https.ready"), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("httpsReady"))
            else -> InstallSteps(context)
        }
        Note(tr("https.firefox"))
    } else if (!on) Note(tr("https.remove_hint"))

    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(tr("https.confirm_title")) },
        text = { Text(tr("https.confirm_body")) },
        confirmButton = {
            TextButton(onClick = {
                confirm = false; on = true; settings.httpsFiltering = true
                restartVpnIfRunning(context)
            }) { Text(tr("https.turn_on")) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("common.cancel")) } },
    )
}

/** The three steps to trust AdWizz's certificate (Android only allows this from its own settings). */
@Composable
private fun InstallSteps(context: Context) {
    val scope = rememberCoroutineScope()
    Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(tr("https.not_ready"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        Text(tr("https.step1"), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { if (Build.VERSION.SDK_INT >= 29) saveCertificate(context) else false }
                toast(context, if (ok) tr("https.saved") else tr("https.save_failed"))
            }
        }, modifier = Modifier.testTag("httpsSave")) { Text(tr("https.save")) }
        Text(tr("https.step2"), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { open(context, AndroidSettings.ACTION_SECURITY_SETTINGS) }) { Text(tr("https.open_security")) }
        Text(tr("https.step3"), style = MaterialTheme.typography.bodySmall)
    }
}

/** Saves the CA certificate to Downloads as "AdWizz-HTTPS.crt" (public; the key never leaves the keystore). */
@RequiresApi(29)
private fun saveCertificate(context: Context): Boolean = runCatching {
    val cert = CertAuthority(context.applicationContext).certificate
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, "AdWizz-HTTPS.crt")
        put(MediaStore.Downloads.MIME_TYPE, "application/x-x509-ca-cert")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
    resolver.openOutputStream(uri)?.use { it.write(CertAuthority.pem(cert).toByteArray()) } ?: return false
    true
}.getOrDefault(false)
