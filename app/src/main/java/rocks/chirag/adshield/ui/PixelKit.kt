package rocks.chirag.adshield.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.floor
import kotlin.math.sin

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

/**
 * Seconds since this composable appeared, updated every display frame (60/90/120 Hz) so motion
 * is fluid — only while ambient motion is on. Readers use it in the draw phase only, and the
 * heavy static scenery sits on a separate layer, so each frame redraws just a few dozen shapes.
 */
@Composable
fun rememberSceneClock(): State<Float> {
    val animate = ambientMotion()
    return produceState(0f, animate) {
        if (!animate) return@produceState
        val start = withFrameNanos { it }
        while (true) withFrameNanos { value = (it - start) / 1e9f }
    }
}

/**
 * The design's backdrop: sky, drifting blocky clouds, a castle and purple pine forest along the
 * bottom; at night a pixel moon and twinkling stars. Drawn in the draw phase only, so its
 * per-frame animation never recomposes anything.
 */
@Composable
fun PixelSky(modifier: Modifier = Modifier, groundY: Float? = null) {
    val c = sceneColors()
    val dark = isAppInDarkTheme()
    val clock = rememberSceneClock()
    val bat = remember { spriteBitmap(DesignArt.BAT, DesignArt.BAT_PALETTE) }
    Box(modifier.fillMaxSize()) {
        // Moving layer: only sky, stars, moon and clouds (a few dozen rects) are redrawn per tick.
        Canvas(Modifier.fillMaxSize()) {
            val t = clock.value
            val p = PX.toPx()
            drawRect(c.sky)
            if (dark) drawStars(p, t, c.star)   // the moon itself is the theme toggle in the top bar
            // Clouds drift slowly to the right and wrap around.
            CLOUDS.forEachIndexed { i, (fx, fy, scale) ->
                val span = size.width + 40 * p
                val x = ((fx * size.width + t * (6f + i * 2.5f) * p / 4f) % span) - 20 * p
                drawCloud(x, fy * size.height, p * scale, c.cloud, c.cloudShade)
            }
            // The design's bats flap across the sky every so often.
            BATS.forEach { (speed, fy, delay) ->
                val lap = size.width + 40 * p
                val x = size.width + 20 * p - ((t * speed * p + delay * lap) % (lap * 1.8f))
                if (x < -20 * p || x > size.width + 20 * p) return@forEach
                val y = fy * size.height + sin(t * 3f + delay * 6) * 3 * p
                val flap = if (sin(t * 14f + delay * 9) > 0) 1f else 0.55f   // wings up / down
                val w = DesignArt.BAT[0].length * p * 0.75f; val h = DesignArt.BAT.size * p * 0.75f
                scale(1f, flap, Offset(x + w / 2, y + h / 2)) {
                    drawImage(bat, dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(w.toInt(), h.toInt()), filterQuality = FilterQuality.None)
                }
            }
        }
        // Static layer: castle and forest (~2000 rects). It never reads the clock, so it's recorded
        // once and reused every frame instead of being redrawn.
        Canvas(Modifier.fillMaxSize()) {
            val p = PX.toPx()
            // Scenery stands on the ground line (bottom of the wizard stage); below it is forest floor.
            val ground = groundY ?: size.height
            drawCastle(size.width * 0.70f, ground, p, c)
            drawForest(ground, p, c.treesFar, rows = 0)
            drawForest(ground, p, c.trees, rows = 1)
            if (ground < size.height) drawRect(c.trees, Offset(0f, ground), Size(size.width, size.height - ground))
        }
    }
}

/** Bats: (speed in px-cells per second, height as fraction of screen, start delay). */
private val BATS = listOf(Triple(14f, 0.10f, 0.1f), Triple(10f, 0.22f, 0.55f), Triple(17f, 0.16f, 0.9f))

private val CLOUDS = listOf(
    Triple(0.05f, 0.08f, 1.4f), Triple(0.55f, 0.04f, 1.0f), Triple(0.30f, 0.30f, 1.2f),
    Triple(0.80f, 0.42f, 1.5f), Triple(0.10f, 0.58f, 1.0f), Triple(0.62f, 0.70f, 1.3f),
)

private val CLOUD_ROWS = listOf(5 to 6, 2 to 13, 0 to 18, 1 to 17, 4 to 10)

private fun DrawScope.drawCloud(x: Float, y: Float, p: Float, color: Color, shade: Color) {
    CLOUD_ROWS.forEachIndexed { row, (start, len) ->
        drawRect(if (row == CLOUD_ROWS.lastIndex) shade else color, Offset(x + start * p, y + row * p), Size(len * p, p))
    }
}

private fun DrawScope.drawForest(h: Float, p: Float, color: Color, rows: Int) {
    // Back row is taller and offset so the two rows interleave.
    val base = h - if (rows == 0) 6 * p else 0f
    var x = if (rows == 0) -4 * p else -9 * p
    var i = 0
    while (x < size.width + 10 * p) {
        val tall = (if (rows == 0) 22 else 15) + (i * 7 % 5) * 2
        for (step in 0 until tall) {
            val half = 1 + step / 2
            drawRect(color, Offset(x - half * p, base - (tall - step) * p), Size((half * 2 + 1) * p, p))
        }
        drawRect(color, Offset(x - 5 * p, base - 2 * p), Size(11 * p, 2 * p + 1))
        x += (if (rows == 0) 11 else 9) * p
        i++
    }
}

private fun DrawScope.drawCastle(x: Float, h: Float, p: Float, c: SceneColors) {
    val ground = h - 14 * p
    fun tower(tx: Float, w: Int, tall: Int) {
        drawRect(c.castle, Offset(tx, ground - tall * p), Size(w * p, tall * p))
        for (k in 0..w / 2) { // pointed roof
            drawRect(c.castleRoof, Offset(tx + k * p, ground - (tall + 1 + k) * p), Size((w - 2 * k) * p, p))
        }
        drawRect(c.ink.copy(alpha = 0.35f), Offset(tx + (w / 2) * p, ground - (tall - 4) * p), Size(p, 2 * p))
    }
    drawRect(c.castle, Offset(x, ground - 14 * p), Size(30 * p, 14 * p))
    tower(x - 3 * p, 7, 26)
    tower(x + 12 * p, 6, 34)
    tower(x + 27 * p, 7, 24)
}

private val STARS = List(36) { i ->
    val r = java.util.Random(i * 7919L)
    Triple(r.nextFloat(), r.nextFloat() * 0.6f, r.nextFloat() * 6f)
}

private fun DrawScope.drawStars(p: Float, t: Float, color: Color) {
    STARS.forEach { (fx, fy, phase) ->
        val a = 0.55f + 0.45f * sin(t * 1.7f + phase)
        val sx = floor(fx * size.width / p) * p
        val sy = floor(fy * size.height / p) * p
        drawRect(color.copy(alpha = a.coerceIn(0f, 1f)), Offset(sx, sy), Size(p, p))
    }
}


/**
 * Pixel-art sun with pulsing rays, or a crescent moon with a twinkling star. Used as the theme
 * switch, sitting in the sky.
 */
@Composable
fun PixelCelestial(moon: Boolean, modifier: Modifier = Modifier) {
    val clock = rememberSceneClock()
    val c = sceneColors()
    Canvas(modifier) {
        val t = clock.value
        val p = size.minDimension / 16f          // 16x16 pixel grid
        val cx = 8; val cy = 8
        fun cell(x: Int, y: Int, color: Color) = drawRect(color, Offset(x * p, y * p), Size(p + 0.5f, p + 0.5f))
        if (!moon) {
            val sun = Color(0xFFF4C95D); val shade = Color(0xFFE0A93A); val ray = Color(0xFFF7D77E)
            val ink = Color(0xFF3A1215)
            // Rays: alternate long/short and swap every half second.
            val long = ((t * 2).toInt() % 2 == 0)
            val dirs = listOf(0 to -1, 1 to -1, 1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1)
            dirs.forEachIndexed { i, (dx, dy) ->
                val len = if ((i % 2 == 0) == long) 2 else 1
                for (k in 0 until len) cell(cx + dx * (6 + k), cy + dy * (6 + k), ray)
            }
            for (y in -4..4) for (x in -4..4) {
                val d = x * x + y * y
                if (d <= 20) cell(cx + x, cy + y, if (d >= 13) ink else if (x + y >= 3) shade else sun)
            }
            cell(cx - 2, cy - 2, Color.White); cell(cx - 1, cy - 2, Color.White)
        } else {
            for (y in -5..5) for (x in -5..5) {
                if (x * x + y * y <= 25 && (x - 3) * (x - 3) + (y + 2) * (y + 2) > 16) {
                    cell(cx + x, cy + y, c.moon)
                }
            }
            cell(cx - 3, cy + 1, Color(0xFFD9CFA8)); cell(cx - 1, cy + 3, Color(0xFFD9CFA8))
            val tw = 0.4f + 0.6f * ((sin(t * 3) + 1) / 2)
            val star = c.star.copy(alpha = tw)
            cell(13, 2, star); cell(12, 2, star.copy(alpha = tw * 0.5f)); cell(14, 2, star.copy(alpha = tw * 0.5f))
            cell(13, 1, star.copy(alpha = tw * 0.5f)); cell(13, 3, star.copy(alpha = tw * 0.5f))
        }
    }
}
