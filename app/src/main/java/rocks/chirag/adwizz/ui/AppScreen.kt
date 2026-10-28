package rocks.chirag.adwizz.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import rocks.chirag.adwizz.data.*

/** The three screens in the bottom bar. Settings and Devices get the whole screen so they're easy to read. */
private enum class Page(val label: String, val tag: String) {
    HOME("Home", "tab_home"), SETTINGS("Settings", "tab_settings"), DEVICES("Devices", "tab_devices")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScreen(onToggle: () -> Unit) {
    val status by VpnState.status.collectAsStateWithLifecycle()
    val error by VpnState.error.collectAsStateWithLifecycle()
    val stats by VpnState.stats.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var groundY by remember { mutableStateOf<Float?>(null) }
    val screen = Page.entries[tab]

    Box(Modifier.fillMaxSize()) {
        PixelSky(groundY = if (screen == Page.HOME) groundY else null)
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (screen == Page.HOME) "AdWizz" else screen.label,
                            style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp
                        )
                    },
                    actions = { ThemeQuickToggle() },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Page.entries.forEach { s ->
                        NavigationBarItem(
                            selected = screen == s,
                            onClick = { tab = s.ordinal },
                            icon = {
                                Icon(
                                    when (s) {
                                        Page.HOME -> Icons.Default.Home
                                        Page.SETTINGS -> Icons.Default.Settings
                                        Page.DEVICES -> Icons.Default.Share
                                    }, null
                                )
                            },
                            label = { Text(s.label) },
                            modifier = Modifier.testTag(s.tag),
                        )
                    }
                }
            }
        ) { pad ->
            // Slide + fade between screens, direction following the bar order.
            AnimatedContent(
                targetState = screen, label = "screens", modifier = Modifier.padding(pad).fillMaxSize(),
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    (slideInHorizontally(tween(260)) { it / 6 * dir } + fadeIn(tween(260)))
                        .togetherWith(slideOutHorizontally(tween(200)) { -it / 6 * dir } + fadeOut(tween(160)))
                }
            ) { s ->
                when (s) {
                    Page.HOME -> HomeScreen(status, error, stats, onToggle) { groundY = it }
                    Page.SETTINGS -> FullScreenPanel { SettingsTab() }
                    Page.DEVICES -> FullScreenPanel { DevicesTab() }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(status: Status, error: String?, stats: Stats, onToggle: () -> Unit, onGround: (Float) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        WizardStage(status, Modifier.onGloballyPositioned { onGround(it.boundsInRoot().bottom) })
        UpdatePanel()
        StatusPanel(status, onToggle)
        if (status == Status.ERROR && error != null) {
            Text(
                error, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        PrivateDnsWarning()
        StatsRow(stats)
        PixelPanel(Modifier.fillMaxWidth().weight(1f).padding(start = 10.dp, end = 6.dp, top = 4.dp)) {
            Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.List, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Recent activity", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            ActivityTab()
        }
    }
}

/** An opaque page over the sky, so long text stays readable. */
@Composable
private fun FullScreenPanel(content: @Composable () -> Unit) {
    PixelPanel(Modifier.fillMaxSize().padding(start = 10.dp, end = 6.dp, bottom = 4.dp)) { content() }
}
