package rocks.chirag.adwizz.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor
import kotlin.math.sin

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
internal val BATS = listOf(Triple(14f, 0.10f, 0.1f), Triple(10f, 0.22f, 0.55f), Triple(17f, 0.16f, 0.9f))

internal val CLOUDS = listOf(
    Triple(0.05f, 0.08f, 1.4f), Triple(0.55f, 0.04f, 1.0f), Triple(0.30f, 0.30f, 1.2f),
    Triple(0.80f, 0.42f, 1.5f), Triple(0.10f, 0.58f, 1.0f), Triple(0.62f, 0.70f, 1.3f),
)
