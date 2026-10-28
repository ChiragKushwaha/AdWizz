package rocks.chirag.adwizz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small drawn "screenshots" of the Android screens in the Reels Blocker setup guide. Drawn in
 * Compose rather than shipped as images, so they cost almost nothing in app size and follow the theme.
 */

private val Tap = Color(0xFFE5484D)

/** A mini phone screen with a title bar. */
@Composable
private fun MockScreen(title: String, description: String, menu: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxWidth().semantics { contentDescription = description }
            .border(2.dp, c.outline, RoundedCornerShape(10.dp)).background(c.surface, RoundedCornerShape(10.dp)).padding(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("‹  $title", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (menu) Text("⋮", fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.tapMark().padding(horizontal = 6.dp))
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** Red ring + dot: "tap here". */
private fun Modifier.tapMark() = border(2.dp, Tap, RoundedCornerShape(6.dp))

@Composable
private fun MockRow(label: String, on: Boolean?, highlighted: Boolean = false, dim: Boolean = false) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().then(if (highlighted) Modifier.tapMark() else Modifier).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 11.sp, color = if (dim) c.onSurfaceVariant.copy(alpha = 0.5f) else c.onSurface, modifier = Modifier.weight(1f))
        if (on != null) {
            Box(
                Modifier.size(26.dp, 14.dp).background(if (on) c.primary else c.outlineVariant, RoundedCornerShape(7.dp)),
                contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
            ) { Box(Modifier.padding(2.dp).size(10.dp).background(c.surface, CircleShape)) }
        }
    }
}

/** Step 1: Accessibility → the add-on → Android's "Restricted setting" message. */
@Composable
internal fun MockRestricted() = MockScreen("Accessibility", "Accessibility settings showing the Restricted setting message") {
    MockRow("AdWizz Reels Blocker", on = false, dim = true)
    Column(
        Modifier.fillMaxWidth().padding(top = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)).padding(8.dp)
    ) {
        Text("Restricted setting", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text("For your security, this setting is currently unavailable.", fontSize = 10.sp)
        Text("OK", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.End).tapMark().padding(horizontal = 8.dp, vertical = 2.dp))
    }
}

/** Step 2: the add-on's App info → ⋮ → "Allow restricted settings". */
@Composable
internal fun MockAllowRestricted() = MockScreen("App info", "App info for AdWizz Reels Blocker: tap the three dots, then Allow restricted settings", menu = true) {
    Row {
        Text("AdWizz Reels Blocker", fontSize = 11.sp, modifier = Modifier.weight(1f))
        Column(
            Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(6.dp)).padding(2.dp)
        ) { MockRow("Allow restricted settings", on = null, highlighted = true) }
    }
}

/** Step 3: Accessibility → Installed apps → the add-on, switched on. */
@Composable
internal fun MockTurnOn() = MockScreen("Installed apps", "Accessibility, Installed apps: turn on AdWizz Reels Blocker") {
    MockRow("AdWizz Reels Blocker", on = true, highlighted = true)
}
