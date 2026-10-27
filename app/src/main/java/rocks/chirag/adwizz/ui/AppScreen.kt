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
                UpdatePanel()
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
