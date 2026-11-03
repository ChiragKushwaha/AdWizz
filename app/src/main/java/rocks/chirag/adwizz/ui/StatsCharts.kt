package rocks.chirag.adwizz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.chirag.adwizz.data.BlockCategory
import rocks.chirag.adwizz.tr

/** Series colors, validated for colour-blind separation on the app's light and dark panels. */
internal object ChartColors {
    private val light = listOf(Color(0xFF2A78D6), Color(0xFFEB6834), Color(0xFF1BAF7A))
    private val dark = listOf(Color(0xFF3987E5), Color(0xFFD95926), Color(0xFF199E70))

    @Composable fun of(c: BlockCategory): Color =
        (if (isAppInDarkTheme()) dark else light)[c.ordinal.coerceAtMost(2)]
}

/** The categories drawn in the per-day chart (app blocks are shown in the tiles only). */
internal val CHART_SERIES = listOf(BlockCategory.ADS, BlockCategory.TRACKERS, BlockCategory.THREATS)

internal fun categoryLabel(c: BlockCategory) = when (c) {
    BlockCategory.ADS -> tr("stats.ads")
    BlockCategory.TRACKERS -> tr("stats.trackers")
    BlockCategory.THREATS -> tr("stats.threats")
    BlockCategory.FIREWALL -> tr("stats.firewall")
}

/**
 * One horizontal bar per app: name above, bar scaled to the biggest, count at the end in text
 * (so the number never depends on telling colours apart). Tapping a row calls [onClick].
 */
@Composable
internal fun AppBarChart(rows: List<Pair<String, Long>>, color: Color, tag: String, onClick: (Int) -> Unit) {
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    Column(Modifier.fillMaxWidth().testTag(tag)) {
        rows.forEachIndexed { i, (label, value) ->
            Column(
                Modifier.fillMaxWidth().clickable { onClick(i) }.padding(vertical = 5.dp)
                    .semantics { contentDescription = "$label: $value" }
            ) {
                Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(10.dp)) {
                        Box(
                            Modifier.fillMaxWidth((value.toFloat() / max).coerceIn(0.02f, 1f)).fillMaxHeight()
                                .background(color, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                        )
                    }
                    Text(
                        compact(value), style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp).widthIn(min = 36.dp),
                    )
                }
            }
        }
    }
}

/**
 * Stacked bars per day (ads, trackers, threats). Tap a day to read its numbers above the chart;
 * the whole column is the touch target, not just the bar.
 */
@Composable
internal fun DailyChart(days: List<Pair<Int, LongArray>>) {
    var selected by remember(days.size) { mutableIntStateOf(days.lastIndex) }
    val colors = CHART_SERIES.map { ChartColors.of(it) }
    val max = days.maxOfOrNull { (_, c) -> CHART_SERIES.sumOf { c[it.ordinal] } }?.coerceAtLeast(1) ?: 1
    val gap = if (days.size > 14) 2.dp else 6.dp
    days.getOrNull(selected)?.let { (day, c) ->
        Text(
            dayLabel(day) + " · " + CHART_SERIES.joinToString(" · ") { "${categoryLabel(it)} ${c[it.ordinal]}" },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("dailySelected"),
        )
    }
    Row(Modifier.fillMaxWidth().height(CHART_HEIGHT + 8.dp).padding(top = 6.dp).testTag("dailyChart"), verticalAlignment = Alignment.Bottom) {
        days.forEachIndexed { i, (_, c) ->
            Column(
                Modifier.weight(1f).fillMaxHeight().padding(horizontal = gap / 2).clickable { selected = i },
                verticalArrangement = Arrangement.Bottom,
            ) {
                val dim = if (i == selected) 1f else 0.55f
                // Top segment first; each gets the rounded top only if it's the topmost drawn.
                val parts = CHART_SERIES.indices.reversed().filter { c[CHART_SERIES[it].ordinal] > 0 }
                parts.forEachIndexed { k, s ->
                    val v = c[CHART_SERIES[s].ordinal]
                    val shape = if (k == 0) RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp) else RoundedCornerShape(0.dp)
                    Box(
                        Modifier.fillMaxWidth().height((CHART_HEIGHT.value * v / max).dp.coerceAtLeast(2.dp))
                            .background(colors[s].copy(alpha = dim), shape)
                    )
                    if (k < parts.lastIndex) Spacer(Modifier.height(2.dp))   // surface gap between segments
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        days.firstOrNull()?.let { Text(dayLabel(it.first), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        days.lastOrNull()?.let { Text(dayLabel(it.first), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    Legend()
}

private val CHART_HEIGHT = 112.dp

@Composable
internal fun Legend() {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        CHART_SERIES.forEach { c ->
            Box(Modifier.size(10.dp).background(ChartColors.of(c), RoundedCornerShape(2.dp)))
            Text(categoryLabel(c), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 4.dp, end = 12.dp))
        }
    }
}

/** "3 Nov" from 20261103, in the phone's language. */
internal fun dayLabel(day: Int): String {
    val cal = java.util.Calendar.getInstance().apply { set(day / 10000, day / 100 % 100 - 1, day % 100) }
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(java.util.Locale.getDefault(), "dMMM")
    return android.text.format.DateFormat.format(pattern, cal).toString()
}

internal fun compact(n: Long): String = if (n > Int.MAX_VALUE) "%.1fB".format(n / 1e9) else compact(n.toInt())
