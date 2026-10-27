package rocks.chirag.adshield.ui

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.core.net.toUri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rocks.chirag.adshield.data.*
import rocks.chirag.adshield.vpn.AdBlockVpnService

private val Danger = Color(0xFFDC2626)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(onToggle: () -> Unit) {
    val status by VpnState.status.collectAsStateWithLifecycle()
    val error by VpnState.error.collectAsStateWithLifecycle()
    val stats by VpnState.stats.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var groundY by remember { mutableStateOf<Float?>(null) }

    Box(Modifier.fillMaxSize()) {
        PixelSky(groundY = groundY)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            "AdWizz", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp
                        )
                    },
                    actions = { ThemeQuickToggle() },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                WizardStage(status, Modifier.onGloballyPositioned { groundY = it.boundsInRoot().bottom })
                StatusPanel(status, onToggle)
                if (status == Status.ERROR && error != null) {
                    Text(
                        error!!, color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                PrivateDnsWarning()
                StatsRow(stats)
                PixelPanel(Modifier.fillMaxWidth().weight(1f).padding(start = 10.dp, end = 6.dp, top = 4.dp)) {
                    TabRow(
                        selectedTabIndex = tab,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Tab(tab == 0, { tab = 0 }, Modifier.testTag("tab_activity"), text = { Text("Activity") },
                            icon = { Icon(Icons.AutoMirrored.Filled.List, null) })
                        Tab(tab == 1, { tab = 1 }, Modifier.testTag("tab_settings"), text = { Text("Settings") },
                            icon = { Icon(Icons.Default.Settings, null) })
                        Tab(tab == 2, { tab = 2 }, Modifier.testTag("tab_devices"), text = { Text("Devices") },
                            icon = { Icon(Icons.Default.Share, null) })
                    }
                    // Slide + fade between tabs, direction following the tab order.
                    AnimatedContent(
                        targetState = tab, label = "tabs",
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(260)))
                                .togetherWith(slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(160)))
                        }
                    ) { t ->
                        when (t) {
                            0 -> ActivityTab()
                            1 -> SettingsTab()
                            else -> DevicesTab()
                        }
                    }
                }
            }
        }
    }
}

/**
 * The pixel sun (day) or moon (night) up in the sky is the theme switch: tap it and the other
 * theme spreads out from it. Drawn as part of the scenery rather than as a button.
 */
@Composable
private fun ThemeQuickToggle() {
    val reveal = LocalThemeReveal.current
    val dark = isAppInDarkTheme()
    var center by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.size(56.dp)
            .testTag("themeToggle")
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                role = androidx.compose.ui.semantics.Role.Button,
            ) { switchTheme(reveal, if (dark) ThemeMode.LIGHT else ThemeMode.DARK, center) }
            .semantics { contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode" },
        contentAlignment = Alignment.Center
    ) {
        // Sun sets / moon rises: the new body scales and fades in as the old one goes.
        AnimatedContent(
            targetState = dark, label = "celestial",
            transitionSpec = {
                (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.4f))
                    .togetherWith(fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.4f))
            }
        ) { isDark -> PixelCelestial(moon = isDark, modifier = Modifier.size(48.dp)) }
    }
}

/** Saves and applies a theme. Called by [ThemeRevealHost] once it has snapshotted the old theme. */
fun applyTheme(context: Context, mode: ThemeMode) {
    Settings(context).themeMode = mode.key
    ThemeState.mode.value = mode
}

private fun switchTheme(reveal: ThemeRevealController?, mode: ThemeMode, origin: Offset) {
    if (reveal != null) reveal.switchTo(mode, origin) else ThemeState.mode.value = mode
}

/** Status line plus the big pixel button that starts (casts the spell) or stops protection. */
@Composable
private fun StatusPanel(status: Status, onToggle: () -> Unit) {
    val on = status == Status.RUNNING
    PixelPanel(Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 2.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(status, label = "statusText", transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }) { st ->
                    Text(
                        when (st) {
                            Status.RUNNING -> "Protected"
                            Status.STARTING -> "Starting…"
                            Status.ERROR -> "Couldn't start"
                            Status.STOPPED -> "Not protected"
                        },
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = if (st == Status.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("statusText")
                    )
                }
                Text(
                    if (on) "Ads and trackers are blocked in every app" else "Tap to block ads across your phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            PixelButton(
                text = when (status) {
                    Status.RUNNING -> "Stop"
                    Status.STARTING -> "Casting…"
                    else -> "Cast spell"
                },
                onClick = onToggle,
                modifier = Modifier.testTag("power")
                    .semantics { contentDescription = if (on) "Turn protection off" else "Turn protection on" },
            )
        }
    }
}

@Composable
private fun PrivateDnsWarning() {
    val context = LocalContext.current
    var strict by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        strict = isPrivateDnsStrict(context)
        onPauseOrDispose { }
    }
    if (!strict) return
    PixelPanel(
        Modifier.fillMaxWidth().padding(start = 10.dp, end = 6.dp, top = 4.dp),
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Private DNS is bypassing AdWizz", fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer)
            Text("Set Private DNS to Off or Automatic, otherwise ads won't be blocked.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = { open(context, AndroidSettings.ACTION_WIRELESS_SETTINGS) }) { Text("Fix it") }
        }
    }
}

@Composable
private fun StatsRow(stats: Stats) {
    val pct = if (stats.queries > 0) (stats.blocked * 100 / stats.queries) else 0
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp, top = 4.dp)) {
        StatCard("Blocked", stats.blocked.toString(), Modifier.weight(1f))
        StatCard("Queries", stats.queries.toString(), Modifier.weight(1f))
        StatCard("Ratio", "$pct%", Modifier.weight(1f))
        StatCard("List", compact(stats.listSize), Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    PixelPanel(modifier.padding(start = 4.dp), shadow = 3.dp) {
        Column(Modifier.padding(vertical = 6.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            // Numbers roll upward as they change.
            AnimatedContent(
                value, label = "stat",
                transitionSpec = {
                    (slideInVertically(tween(200)) { it / 2 } + fadeIn(tween(200)))
                        .togetherWith(slideOutVertically(tween(150)) { -it / 2 } + fadeOut(tween(150)))
                }
            ) { v ->
                Text(v, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("stat_$label"))
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun compact(n: Int) = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 1_000 -> "${n / 1000}k"
    else -> n.toString()
}

// ---------------- Activity ----------------

@Composable
private fun ActivityTab() {
    val log by VpnState.log.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selected by remember { mutableStateOf<QueryLog?>(null) }

    if (log.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "DNS requests from your apps will show up here once protection is on.",
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
                        if (entry.blocked) "BLOCKED" else "allowed",
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
                    if (entry.blocked) "This domain is blocked. Allow it if an app or site is broken."
                    else "This domain is allowed. Block it if it serves ads or tracking."
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
                }) { Text(if (entry.blocked) "Allow" else "Block") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } }
        )
    }
}

// ---------------- Settings ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTab() {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val scope = rememberCoroutineScope()
    val updating by VpnState.updatingLists.collectAsStateWithLifecycle()

    var lastUpdate by remember { mutableLongStateOf(settings.lastListUpdate) }
    var savedDns by remember { mutableStateOf(settings.primaryDns to settings.secondaryDns) }
    var dns1 by remember { mutableStateOf(settings.primaryDns) }
    var dns2 by remember { mutableStateOf(settings.secondaryDns) }
    var catchBypass by remember { mutableStateOf(settings.catchHardcodedDns) }
    var blockTrackers by remember { mutableStateOf(settings.blockTrackers) }
    var blockMalicious by remember { mutableStateOf(settings.blockMalicious) }
    var blockGenerated by remember { mutableStateOf(settings.blockGenerated) }
    var excluded by remember { mutableStateOf(settings.excludedApps) }
    var pickingApps by remember { mutableStateOf(false) }
    val themeMode by ThemeState.mode.collectAsStateWithLifecycle()
    val reveal = LocalThemeReveal.current

    /** After a list switch changes: the running VPN reloads and fetches any list not on disk yet. */
    fun listsChanged() = reload(context)
    var sources by remember { mutableStateOf(settings.enabledSources) }
    var allow by remember { mutableStateOf(settings.allowlist.sorted()) }
    var custom by remember { mutableStateOf(settings.customBlocklist.sorted()) }

    LazyColumn(Modifier.fillMaxSize().testTag("settingsList"), contentPadding = PaddingValues(16.dp)) {
        item { SectionTitle("Appearance") }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, mode ->
                    var center by remember { mutableStateOf(Offset.Zero) }
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { switchTheme(reveal, mode, center) },
                        modifier = Modifier.testTag("theme_${mode.key}")
                            .onGloballyPositioned { center = it.boundsInRoot().center },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        label = { Text(mode.label) }
                    )
                }
            }
        }

        item { SectionTitle("Upstream DNS") }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DnsPresets.ALL.forEach { p ->
                    FilterChip(
                        selected = dns1 == p.primary,
                        onClick = {
                            dns1 = p.primary; dns2 = p.secondary
                            settings.primaryDns = p.primary; settings.secondaryDns = p.secondary
                            savedDns = p.primary to p.secondary
                            reload(context)
                        },
                        label = { Text(p.name, fontSize = 12.sp) }
                    )
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(dns1, { dns1 = it.trim() }, label = { Text("Primary") },
                    singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(dns2, { dns2 = it.trim() }, label = { Text("Secondary") },
                    singleLine = true, modifier = Modifier.weight(1f))
            }
            if (dns1 != savedDns.first || dns2 != savedDns.second) {
                TextButton(onClick = {
                    if (isIpv4(dns1) && (dns2.isEmpty() || isIpv4(dns2))) {
                        settings.primaryDns = dns1; settings.secondaryDns = dns2
                        savedDns = dns1 to dns2
                        reload(context)
                    } else toast(context, "Enter IPv4 addresses, e.g. 1.1.1.1")
                }) { Text("Save DNS servers") }
            }
        }

        item { SectionTitle("Protection") }
        item {
            SwitchRow(
                "Block DNS bypass",
                "Catches apps that hard-code Google/Cloudflare/etc. DNS or use DNS-over-HTTPS to them.",
                catchBypass
            ) {
                catchBypass = it
                settings.catchHardcodedDns = it
                if (VpnState.status.value == Status.RUNNING)
                    AdBlockVpnService.send(context, AdBlockVpnService.ACTION_RESTART)
            }
        }
        item {
            SwitchRow(
                "Block trackers & analytics scripts",
                "Analytics, tracking pixels, fingerprinting scripts, disguised first-party trackers " +
                    "and phone-maker telemetry (Samsung, Xiaomi, TikTok…).",
                blockTrackers
            ) {
                blockTrackers = it
                settings.blockTrackers = it
                listsChanged()
            }
        }
        item {
            SwitchRow(
                "Block gibberish ad domains",
                "Catches brand-new ad domains like pgjlctmswgnwf.com before any list has them. " +
                    "If a site breaks, allow it from the Activity tab.",
                blockGenerated
            ) {
                blockGenerated = it
                settings.blockGenerated = it
                listsChanged()
            }
        }
        item {
            SwitchRow(
                "Block malicious & crypto-mining scripts",
                "Phishing and scam sites, and scripts that mine crypto-currency in your browser.",
                blockMalicious
            ) {
                blockMalicious = it
                settings.blockMalicious = it
                listsChanged()
            }
        }

        item { SectionTitle("Excluded apps (VPN hidden from them)") }
        item {
            Text(
                "Some apps (banking, streaming, games) refuse to work when a VPN is on. Excluded apps " +
                    "skip AdWizz completely: they won't see the VPN, but their ads aren't blocked either.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { pickingApps = true }, modifier = Modifier.padding(top = 8.dp)) {
                Text(if (excluded.isEmpty()) "Choose apps" else "${excluded.size} app(s) excluded — edit")
            }
        }

        item { SectionTitle("Blocklists") }
        items(Sources.ADS) { src ->
            SwitchRow(src.name, src.description, src.id in sources) { on ->
                sources = if (on) sources + src.id else sources - src.id
                settings.enabledSources = sources
                listsChanged()
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Button(enabled = !updating, onClick = {
                    // Application context: a download can outlive this screen (e.g. rotation),
                    // and must not keep the old Activity alive while it runs.
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
                    if (lastUpdate == 0L) "Never updated"
                    else "Updated ${DateUtils.getRelativeTimeSpanString(lastUpdate)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { SectionTitle("Allowlist (never blocked)") }
        item {
            DomainAdder("example.com") { d ->
                settings.allowlist = settings.allowlist + d
                allow = settings.allowlist.sorted(); reload(context)
            }
        }
        items(allow, key = { "a-$it" }) { d ->
            DomainRow(d) { settings.allowlist = settings.allowlist - d; allow = settings.allowlist.sorted(); reload(context) }
        }

        item { SectionTitle("My blocked domains") }
        item {
            DomainAdder("ads.example.com") { d ->
                settings.customBlocklist = settings.customBlocklist + d
                custom = settings.customBlocklist.sorted(); reload(context)
            }
        }
        items(custom, key = { "b-$it" }) { d ->
            DomainRow(d) { settings.customBlocklist = settings.customBlocklist - d; custom = settings.customBlocklist.sorted(); reload(context) }
        }

        item { SectionTitle("Make it stick") }
        item {
            PixelPanel(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.padding(16.dp)) {
                    Text("Always-on VPN", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Turn on Always-on for AdWizz so protection survives reboots and the system restarts it if it's killed.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = {
                        // VPN settings screen exists from Android 7; older phones get Network settings.
                        open(context, if (Build.VERSION.SDK_INT >= 24) AndroidSettings.ACTION_VPN_SETTINGS else AndroidSettings.ACTION_WIRELESS_SETTINGS)
                    }) { Text("Open VPN settings") }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    var unrestricted by remember { mutableStateOf(isBatteryUnrestricted(context)) }
                    LifecycleResumeEffect(Unit) {
                        unrestricted = isBatteryUnrestricted(context)
                        onPauseOrDispose { }
                    }
                    Text(
                        if (unrestricted) "Battery: unrestricted ✓" else "Battery: optimised (may be stopped)",
                        fontWeight = FontWeight.SemiBold,
                        color = if (unrestricted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Text(oemHint(), style = MaterialTheme.typography.bodySmall)
                    Row {
                        if (!unrestricted) TextButton(onClick = { requestBatteryExemption(context) }) { Text("Allow background") }
                        TextButton(onClick = { openAppDetails(context) }) { Text("App settings") }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Private DNS must be Off or Automatic", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "If Private DNS is set to a specific hostname (e.g. dns.google), Android sends DNS over TLS directly and skips AdWizz.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TextButton(onClick = { open(context, AndroidSettings.ACTION_WIRELESS_SETTINGS) }) { Text("Open network settings") }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (pickingApps) {
        AppPickerDialog(
            initial = excluded,
            onDismiss = { pickingApps = false },
            onSave = { chosen ->
                pickingApps = false
                excluded = chosen
                settings.excludedApps = chosen
                // Excluded apps are part of the tunnel setup, so it has to be rebuilt.
                if (VpnState.status.value == Status.RUNNING)
                    AdBlockVpnService.send(context, AdBlockVpnService.ACTION_RESTART)
            }
        )
    }
}

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
private fun AppPickerDialog(initial: Set<String>, onDismiss: () -> Unit, onSave: (Set<String>) -> Unit) {
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

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 6.dp).testTag("switch_$title"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag("switchToggle_$title"))
    }
}

@Composable
private fun DomainAdder(hint: String, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            text, { text = it.trim().lowercase() }, placeholder = { Text(hint) },
            singleLine = true, modifier = Modifier.weight(1f).testTag("adder_$hint")
        )
        IconButton(onClick = {
            val d = text.removePrefix("https://").removePrefix("http://").substringBefore('/')
            if (d.contains('.') && d.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }) {
                onAdd(d); text = ""
            } else toast(context, "Enter a domain like $hint")
        }) { Icon(Icons.Default.Add, "Add") }
    }
}

@Composable
private fun DomainRow(domain: String, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(domain, fontFamily = FontFamily.Monospace, fontSize = 13.sp, modifier = Modifier.weight(1f))
        IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, "Remove") }
    }
}

private fun reload(context: Context) {
    if (VpnState.status.value == Status.RUNNING) {
        AdBlockVpnService.send(context, AdBlockVpnService.ACTION_RELOAD)
    }
}

private fun isIpv4(s: String): Boolean {
    val parts = s.split('.')
    return parts.size == 4 && parts.all { p -> p.toIntOrNull()?.let { it in 0..255 } == true }
}

private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

private fun isBatteryUnrestricted(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

/**
 * Opens the system's battery-optimisation list so the user can set AdWizz to "Unrestricted".
 * (Asking directly needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, which Play restricts.)
 */
private fun requestBatteryExemption(context: Context) {
    runCatching { context.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        .onFailure { openAppDetails(context) }
}

private fun openAppDetails(context: Context) {
    runCatching {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())
        )
    }
}

/** Phone makers that aggressively kill background apps each hide the switch in a different place. */
private fun oemHint(): String = when (Build.MANUFACTURER.lowercase()) {
    "xiaomi", "redmi", "poco" ->
        "On Xiaomi/Redmi/POCO: App settings → Autostart ON, and Battery saver → No restrictions. Also lock AdWizz in Recents."
    "oppo", "realme", "oneplus" ->
        "On Oppo/Realme/OnePlus: App settings → Battery → Allow background activity, and allow Auto launch."
    "vivo", "iqoo" ->
        "On Vivo/iQOO: Settings → Battery → Background power consumption → allow AdWizz, and enable Autostart."
    "samsung" ->
        "On Samsung: App settings → Battery → Unrestricted, and keep AdWizz out of 'Sleeping apps'."
    "huawei", "honor" ->
        "On Huawei/Honor: Battery → App launch → AdWizz → Manage manually, all switches on."
    else ->
        "Set AdWizz's battery usage to Unrestricted so the system doesn't stop it in the background."
}

/** True when Private DNS is set to a specific hostname, which makes Android bypass any VPN's DNS. */
fun isPrivateDnsStrict(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < 28) return false
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    return runCatching { cm.getLinkProperties(cm.activeNetwork)?.privateDnsServerName != null }.getOrDefault(false)
}

private fun open(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action)) }
        .onFailure { context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS)) }
}
