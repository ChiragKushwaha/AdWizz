package rocks.chirag.adwizz.ui

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
            Text("This link expired", fontWeight = FontWeight.SemiBold)
            Text("Setup links stop working after 15 minutes so nobody else can use them.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRenew) { Text("Make a new link") }
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
            Text("Allow AdWizz to reach nearby devices", fontWeight = FontWeight.SemiBold)
            Text(
                "Android needs your OK before other devices on your hotspot or Wi-Fi can open the setup " +
                    "page. AdWizz only uses it for this page." +
                    if (refused) "\n\nIn App info, tap Permissions → Nearby devices → Allow." else "",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onAllow) { Text(if (refused) "Open App info" else "Allow") }
        }
    }
}

/** Shown while neither the hotspot nor Wi-Fi is on, so other devices can't reach the phone. */
@Composable
internal fun NoNetwork() {
    val context = LocalContext.current
    PixelPanel(Modifier.fillMaxWidth().testTag("noNetwork"), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(16.dp)) {
            Text("Turn on your hotspot or join a Wi-Fi network", fontWeight = FontWeight.SemiBold)
            Text("The QR code appears here as soon as other devices can reach this phone.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                runCatching { context.startActivity(Intent(AndroidSettings.ACTION_WIRELESS_SETTINGS)) }
            }) { Text("Open hotspot settings") }
        }
    }
}

/** Per-platform setup steps under the QR code. */
@Composable
internal fun PlatformInstructions() {
    val clipboard = LocalClipboardManager.current
    Spacer(Modifier.height(16.dp))
    PlatformCard("iPhone, iPad, Mac",
        "Scan with the Camera app → open the link → Download setup profile → install it in Settings. One-time setup.")
    PlatformCard("Android",
        "Android can't be configured by a link. On that phone set Private DNS to the hostname below " +
            "(Settings → Network & internet → Private DNS):",
        copyValue = ShareContent.DOT_HOST, onCopy = { clipboard.setText(AnnotatedString(it)) })
    PlatformCard("Windows / laptops",
        "Open the link above in the laptop's browser for steps, or set DNS to ${ShareContent.DNS4[0]} and ${ShareContent.DNS4[1]}.")
    Text(
        "Other devices use AdGuard's free ad-blocking DNS, not this phone's lists, and stay protected " +
            "after leaving your hotspot. This page is only served while this screen is open.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp)
    )
}
