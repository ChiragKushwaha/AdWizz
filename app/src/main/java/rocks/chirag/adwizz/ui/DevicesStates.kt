package rocks.chirag.adwizz.ui

import rocks.chirag.adwizz.tr
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import rocks.chirag.adwizz.share.ShareContent

/** Shown when the setup link timed out (links only live 15 minutes). */
@Composable
internal fun ExpiredLink(onRenew: () -> Unit) {
    PixelPanel(Modifier.fillMaxWidth().testTag("expired")) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("devices.expired"), fontWeight = FontWeight.SemiBold)
            Text(tr("devices.expired_body"), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRenew) { Text(tr("devices.new_link")) }
        }
    }
}

internal const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/**
 * Android 17 blocks apps from talking to devices on the local network (hotspot or Wi-Fi) until
 * the user allows "Nearby devices". Without it the setup page can't reach the other device.
 */
internal fun localNetworkAllowed(context: Context) = Build.VERSION.SDK_INT < 37 ||
    ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED

/** Asks for the local network permission; after a refusal [refused] sends the user to App info. */
@Composable
internal fun LocalNetworkNeeded(refused: Boolean, onAllow: () -> Unit) {
    PixelPanel(Modifier.fillMaxWidth().testTag("localNetwork"), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("devices.lan_title"), fontWeight = FontWeight.SemiBold)
            Text(
                tr("devices.lan_body") +
                    if (refused) "\n\n" + tr("devices.lan_refused") else "",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onAllow) { Text(if (refused) tr("devices.open_info") else tr("common.allow")) }
        }
    }
}

/** Shown while neither the hotspot nor Wi-Fi is on, so other devices can't reach the phone. */
@Composable
internal fun NoNetwork() {
    val context = LocalContext.current
    PixelPanel(Modifier.fillMaxWidth().testTag("noNetwork"), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp)) {
            Text(tr("devices.no_net"), fontWeight = FontWeight.SemiBold)
            Text(tr("devices.no_net_body"), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(AndroidSettings.ACTION_WIRELESS_SETTINGS)) }
            }) { Text(tr("devices.hotspot_settings")) }
        }
    }
}

/** Per-platform setup steps under the QR code. */
@Composable
internal fun PlatformInstructions() {
    val clipboard = LocalClipboardManager.current
    Spacer(Modifier.height(16.dp))
    PlatformCard(tr("devices.apple"),
        tr("devices.apple_body"))
    PlatformCard("Android",
        tr("devices.android_body"),
        copyValue = ShareContent.DOT_HOST, onCopy = { clipboard.setText(AnnotatedString(it)) })
    PlatformCard(tr("devices.windows"),
        tr("devices.windows_body", "dns1" to ShareContent.DNS4[0], "dns2" to ShareContent.DNS4[1]))
    Text(
        tr("devices.footer"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}
