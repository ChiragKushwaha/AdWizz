package rocks.chirag.adshield.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.sin

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
