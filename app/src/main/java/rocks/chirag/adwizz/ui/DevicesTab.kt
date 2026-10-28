package rocks.chirag.adwizz.ui

import rocks.chirag.adwizz.tr
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rocks.chirag.adwizz.share.LanAddress
import rocks.chirag.adwizz.share.ShareContent
import rocks.chirag.adwizz.share.ShareServer

/**
 * "Other devices" tab. Hotspot clients never pass through an Android VPN, so this serves a setup
 * page over the local network and shows its address as a QR code.
 */
@Composable
fun DevicesTab(
    server: ShareServer = SetupServer.instance,
    findIp: () -> String? = { LanAddress.pick(LanAddress.candidates()) },
) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(localNetworkAllowed(context)) }
    var refused by rememberSaveable { mutableStateOf(false) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        refused = !granted
    }
    LifecycleResumeEffect(Unit) {
        allowed = localNetworkAllowed(context)
        onPauseOrDispose { }
    }
    // Ask right away the first time; after a "Don't allow" the card below explains and retries.
    // (Once per visit: rotating while Android's prompt is open mustn't ask twice.)
    LaunchedEffect(Unit) { if (!allowed && !asked) { asked = true; ask.launch(LOCAL_NETWORK_PERMISSION) } }
    var url by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }
    var renew by remember { mutableIntStateOf(0) }

    // Run the server only while this tab is visible; re-check the address in case the hotspot
    // or Wi-Fi is switched on while the screen is open. A rotation keeps it running, so the
    // QR code someone is about to scan (and its secret link) stays the same.
    DisposableEffect(server) { onDispose { if (context.findActivity()?.isChangingConfigurations != true) server.stop() } }
    LaunchedEffect(server, renew, allowed) {
        if (!allowed) { server.stop(); url = null; return@LaunchedEffect }
        while (true) {
            url = withContext(Dispatchers.IO) { serveOn(server, findIp()) }
            checked = true
            delay(3000)
        }
    }

    LazyColumn(Modifier.fillMaxSize().testTag("devices"), contentPadding = PaddingValues(16.dp)) {
        item { DevicesIntro() }
        item {
            AnimatedContent(targetState = url, label = "qr", transitionSpec = { fadeIn() togetherWith fadeOut() }) { current ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    when {
                        !allowed -> LocalNetworkNeeded(refused) {
                            if (refused) openAppDetails(context) else ask.launch(LOCAL_NETWORK_PERMISSION)
                        }
                        current != null -> ShareLink(current)
                        !checked -> CircularProgressIndicator(Modifier.padding(32.dp))
                        server.isExpired -> ExpiredLink { server.stop(); server.clearExpiry(); renew++ }
                        else -> NoNetwork()
                    }
                }
            }
        }
        item { PlatformInstructions() }
    }
}

/**
 * Listens only on the hotspot / Wi-Fi address [ip] (moving with it if it changes) and returns the
 * page URL; stops serving and returns null when there's no local network or the link expired.
 */
private fun serveOn(server: ShareServer, ip: String?): String? {
    if (ip == null || server.isExpired) { server.stop(); return null }
    return runCatching {
        val port = server.start(java.net.InetAddress.getByName(ip))
        LanAddress.url(ip, port, server.token)
    }.getOrElse { server.stop(); null }
}

@Composable
private fun DevicesIntro() {
    Text(tr("devices.title"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    Text(
        tr("devices.intro"),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun ShareLink(url: String) {
    val clipboard = LocalClipboardManager.current
    QrCode(url, Modifier.size(240.dp))
    Spacer(Modifier.height(10.dp))
    Text(url, fontFamily = FontFamily.Monospace, fontSize = 14.sp, modifier = Modifier.testTag("shareUrl"))
    TextButton(onClick = { clipboard.setText(AnnotatedString(url)) }) { Text(tr("devices.copy_link")) }
}

