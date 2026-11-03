package rocks.chirag.adwizz.ui

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import rocks.chirag.adwizz.data.*
import rocks.chirag.adwizz.tr

/** Ranges the Stats tab offers: today, the last 7 days, the last 30 days. */
private val RANGES = listOf(1 to "stats.today", 7 to "stats.week", 30 to "stats.month")
private const val TOP_APPS = 8

/**
 * The dashboard: what was blocked, per day and per app. Everything is counted on the phone
 * (see BlockStats) and refreshed every few seconds while the tab is open.
 */
@Composable
internal fun StatsTab() {
    val context = LocalContext.current
    var range by rememberSaveable { mutableIntStateOf(7) }
    var summary by remember { mutableStateOf<StatsSummary?>(null) }
    var openApp by rememberSaveable { mutableStateOf<String?>(null) }
    // Recount when the numbers change, at most every 2 seconds (StateFlow keeps only the latest change).
    LaunchedEffect(range) {
        BlockStats.version.collect {
            summary = withContext(Dispatchers.Default) { BlockStats.summary(range) }
            delay(2_000)
        }
    }
    val s = summary ?: return
    val label = { pkg: String -> AppNames.label(context, pkg) }

    LazyColumn(Modifier.fillMaxSize().testTag("statsList"), contentPadding = PaddingValues(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RANGES.forEach { (days, key) ->
                    FilterChip(selected = range == days, onClick = { range = days }, label = { Text(tr(key)) },
                        modifier = Modifier.testTag("range_$days"))
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                StatCard(tr("stats.ads"), compact(s.total(BlockCategory.ADS)), Modifier.weight(1f))
                StatCard(tr("stats.trackers"), compact(s.total(BlockCategory.TRACKERS)), Modifier.weight(1f))
                StatCard(tr("stats.threats"), compact(s.total(BlockCategory.THREATS)), Modifier.weight(1f))
                val pct = if (s.queries > 0) s.blocked * 100 / s.queries else 0
                StatCard(tr("home.stat_ratio"), "$pct%", Modifier.weight(1f))
            }
        }
        if (s.queries == 0L) {
            item { Note(tr("stats.empty")) }
            return@LazyColumn
        }
        if (range > 1) item {
            SectionTitle(tr("stats.per_day"))
            DailyChart(s.perDay)
        }
        item { AppSection(tr("stats.ads_by_app"), s, BlockCategory.ADS, label) { openApp = it } }
        item { AppSection(tr("stats.trackers_by_app"), s, BlockCategory.TRACKERS, label) { openApp = it } }
        if (Build.VERSION.SDK_INT < 29) item { Note(tr("stats.needs_android10")) }
        if (s.topDomains.isNotEmpty()) item {
            SectionTitle(tr("stats.top_domains"))
            s.topDomains.forEach { (name, n) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(name, fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(compact(n), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            TextButton(onClick = { BlockStats.clear() }, modifier = Modifier.padding(top = 12.dp)) {
                Text(tr("stats.reset"))
            }
            Note(tr("stats.private"))
        }
    }
    openApp?.let { pkg -> AppStatsDialog(pkg, s, label(pkg)) { openApp = null } }
}

/** "Ads blocked by app": the top apps for one category, as bars. */
@Composable
private fun AppSection(title: String, s: StatsSummary, c: BlockCategory, label: (String) -> String, onOpen: (String) -> Unit) {
    val top = s.perApp.map { it.first to it.second[c.ordinal] }.filter { it.second > 0 }
        .sortedByDescending { it.second }.take(TOP_APPS)
    SectionTitle(title)
    if (top.isEmpty()) { Note(tr("stats.none_yet")); return }
    AppBarChart(top.map { label(it.first) to it.second }, ChartColors.of(c), "chart_${c.name}") { i -> onOpen(top[i].first) }
}

/** One app's numbers, with a switch to cut its internet off ("Block apps' internet"). */
@Composable
private fun AppStatsDialog(pkg: String, s: StatsSummary, name: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { Settings(context) }
    val counts = s.perApp.firstOrNull { it.first == pkg }?.second ?: LongArray(BlockStats.ALLOWED + 1)
    var blocked by remember { mutableStateOf(pkg in settings.firewalledApps) }
    val canFirewall = pkg != AppNames.UNKNOWN && !pkg.startsWith("uid:")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column {
                BlockCategory.entries.forEach { c -> Text("${categoryLabel(c)}: ${counts[c.ordinal]}") }
                Text(tr("stats.allowed_count", "count" to counts[BlockStats.ALLOWED]))
                if (canFirewall) SwitchRow(tr("firewall.block_app"), tr("firewall.block_app_desc"), blocked) { on ->
                    blocked = on
                    settings.firewalledApps = if (on) settings.firewalledApps + pkg else settings.firewalledApps - pkg
                    reload(context)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(tr("common.done")) } },
    )
}

@Composable
internal fun Note(text: String) = Text(
    text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(top = 8.dp),
)
