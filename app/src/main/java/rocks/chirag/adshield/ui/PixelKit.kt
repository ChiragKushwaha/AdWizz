package rocks.chirag.adshield.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Size of one "pixel" in the scenery. */
val PX = 4.dp

/**
 * Panel with a thick dark border and a hard drop shadow, like the button in the design.
 * Replaces Material cards so every surface looks pixel-built.
 */
@Composable
fun PixelPanel(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    shadow: Dp = 4.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ink = MaterialTheme.colorScheme.outline
    Column(
        modifier
            .padding(end = shadow, bottom = shadow)
            .drawBehind {
                val s = shadow.toPx()
                drawRect(ink.copy(alpha = 0.85f), Offset(s, s), size)
            }
            .background(color, RectangleShape)
            .border(2.dp, ink, RectangleShape),
        content = { CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content() } }
    )
}

/** Chunky pixel button: maroon (gold at night) block, dark border, shadow that "presses" in. */
@Composable
fun PixelButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onPrimary,
    enabled: Boolean = true,
) {
    val ink = MaterialTheme.colorScheme.outline
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth by animateDpAsState(if (pressed) 1.dp else 5.dp, spring(stiffness = 900f), label = "depth")
    Box(
        modifier
            .padding(end = 5.dp, bottom = 5.dp)
            .offset(x = 5.dp - depth, y = 5.dp - depth)
            .drawBehind { drawRect(ink, Offset(depth.toPx(), depth.toPx()), size) }
            .background(if (enabled) color else color.copy(alpha = 0.5f), RectangleShape)
            .border(3.dp, ink, RectangleShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text.uppercase(), color = textColor, fontWeight = FontWeight.Bold, fontSize = 17.sp, letterSpacing = 1.sp)
    }
}
