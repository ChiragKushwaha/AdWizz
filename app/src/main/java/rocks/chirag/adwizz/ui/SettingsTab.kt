package rocks.chirag.adwizz.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Settings tab: one scrolling list of independent sections (each keeps its own state). */
@Composable
internal fun SettingsTab() {
    LazyColumn(Modifier.fillMaxSize().testTag("settingsList"), contentPadding = PaddingValues(16.dp)) {
        item { AppearanceSection() }
        item { DnsSection() }
        item { ProtectionSection() }
        item { ExcludedAppsSection() }
        item { BlocklistSection() }
        item { AllowlistSection() }
        item { CustomBlocklistSection() }
        item { UpdatesSection() }
        item { KeepAliveSection() }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
