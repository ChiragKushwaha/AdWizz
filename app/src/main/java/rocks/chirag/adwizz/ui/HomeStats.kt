package rocks.chirag.adwizz.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.chirag.adwizz.data.*

@Composable
internal fun StatsRow(stats: Stats) {
    val pct = if (stats.queries > 0) (stats.blocked * 100 / stats.queries) else 0
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 2.dp, top = 4.dp)) {
        StatCard("Blocked", stats.blocked.toString(), Modifier.weight(1f))
        StatCard("Queries", stats.queries.toString(), Modifier.weight(1f))
        StatCard("Ratio", "$pct%", Modifier.weight(1f))
        StatCard("List", compact(stats.listSize), Modifier.weight(1f))
    }
}

@Composable
internal fun StatCard(label: String, value: String, modifier: Modifier) {
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

internal fun compact(n: Int) = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 1_000 -> "${n / 1000}k"
    else -> n.toString()
}
