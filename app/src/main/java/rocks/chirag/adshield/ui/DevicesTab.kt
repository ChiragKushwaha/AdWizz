package rocks.chirag.adshield.ui

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rocks.chirag.adshield.share.LanAddress
import rocks.chirag.adshield.share.ShareContent
import rocks.chirag.adshield.share.ShareServer

/** Encodes [text] as a QR matrix with no quiet zone (the card around it provides the margin). */
fun qrMatrix(text: String): BitMatrix = QRCodeWriter().encode(
    text, BarcodeFormat.QR_CODE, 0, 0,
    mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
)

/** Always dark-on-white, whatever the theme: scanners need the contrast. */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { qrMatrix(text) }
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(16.dp)
            .semantics { contentDescription = "QR code for $text" }
            .testTag("qr")
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = size.minDimension / matrix.width
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                // +0.5px overlap avoids hairline gaps between cells on fractional densities.
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

/**
 * "Other devices" tab. Hotspot clients never pass through an Android VPN, so this serves a setup
 * page over the local network and shows its address as a QR code.
 */
@Composable
fun DevicesTab(
    server: ShareServer = remember { ShareServer() },
    findIp: () -> String? = { LanAddress.pick(LanAddress.candidates()) },
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var url by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }
    var renew by remember { mutableIntStateOf(0) }

    // Run the server only while this tab is visible; re-check the address in case the hotspot
    // or Wi-Fi is switched on while the screen is open.
    DisposableEffect(server) { onDispose { server.stop() } }
    LaunchedEffect(server, renew) {
        while (true) {
            // Listen only on the hotspot / Wi-Fi address; move with it if it changes, and stop
            // serving entirely when there's no local network.
            url = withContext(Dispatchers.IO) {
                val ip = findIp()
                if (ip == null) { server.stop(); null }
                else if (server.isExpired) { server.stop(); null }
                else runCatching {
                    val port = server.start(java.net.InetAddress.getByName(ip))
                    LanAddress.url(ip, port, server.token)
                }.getOrElse { server.stop(); null }
            }
            checked = true
            delay(3000)
        }
    }

    LazyColumn(Modifier.fillMaxSize().testTag("devices"), contentPadding = PaddingValues(16.dp)) {
        item {
            Text("Protect other devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Devices on your hotspot don't go through AdWizz — Android never sends hotspot traffic " +
                    "through a VPN. Scan this code on the other device (it must be on this phone's hotspot or " +
                    "the same Wi-Fi) to set up ad blocking there in a few taps.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
        }
        item {
            AnimatedContent(
                targetState = url, label = "qr",
                transitionSpec = { fadeIn() togetherWith fadeOut() }
            ) { current ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        current != null -> {
                            QrCode(current, Modifier.size(240.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(current, fontFamily = FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.testTag("shareUrl"))
                            TextButton(onClick = { clipboard.setText(AnnotatedString(current)) }) { Text("Copy link") }
                        }
                        !checked -> CircularProgressIndicator(Modifier.padding(32.dp))
                        server.isExpired -> PixelPanel(Modifier.fillMaxWidth().testTag("expired")) {
                            Column(Modifier.padding(16.dp)) {
                                Text("This link expired", fontWeight = FontWeight.SemiBold)
                                Text("Setup links stop working after 15 minutes so nobody else can use them.", style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { server.stop(); server.clearExpiry(); renew++ }) { Text("Make a new link") }
                            }
                        }
                        else -> PixelPanel(
                            Modifier.fillMaxWidth().testTag("noNetwork"),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Turn on your hotspot or join a Wi-Fi network", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "The QR code appears here as soon as other devices can reach this phone.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                TextButton(onClick = {
                                    runCatching { context.startActivity(Intent(AndroidSettings.ACTION_WIRELESS_SETTINGS)) }
                                }) { Text("Open hotspot settings") }
                            }
                        }
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            PlatformCard(
                "iPhone, iPad, Mac",
                "Scan with the Camera app → open the link → Download setup profile → install it in Settings. One-time setup."
            )
            PlatformCard(
                "Android",
                "Android can't be configured by a link. On that phone set Private DNS to the hostname below " +
                    "(Settings → Network & internet → Private DNS):",
                copyValue = ShareContent.DOT_HOST,
                onCopy = { clipboard.setText(AnnotatedString(it)) }
            )
            PlatformCard(
                "Windows / laptops",
                "Open the link above in the laptop's browser for steps, or set DNS to " +
                    "${ShareContent.DNS4[0]} and ${ShareContent.DNS4[1]}."
            )
            Text(
                "Other devices use AdGuard's free ad-blocking DNS, not this phone's lists, and stay protected " +
                    "after leaving your hotspot. This page is only served while this screen is open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun PlatformCard(title: String, body: String, copyValue: String? = null, onCopy: (String) -> Unit = {}) {
    PixelPanel(Modifier.fillMaxWidth().padding(vertical = 4.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, shadow = 3.dp) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodySmall)
            if (copyValue != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(copyValue, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onCopy(copyValue) }) { Text("Copy") }
                }
            }
        }
    }
}
