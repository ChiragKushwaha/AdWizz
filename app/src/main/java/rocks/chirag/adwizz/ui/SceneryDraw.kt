package rocks.chirag.adwizz.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.floor
import kotlin.math.sin

internal val CLOUD_ROWS = listOf(5 to 6, 2 to 13, 0 to 18, 1 to 17, 4 to 10)

internal fun DrawScope.drawCloud(x: Float, y: Float, p: Float, color: Color, shade: Color) {
    CLOUD_ROWS.forEachIndexed { row, (start, len) ->
        drawRect(if (row == CLOUD_ROWS.lastIndex) shade else color, Offset(x + start * p, y + row * p), Size(len * p, p))
    }
}

internal fun DrawScope.drawForest(h: Float, p: Float, color: Color, rows: Int) {
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

internal fun DrawScope.drawCastle(x: Float, h: Float, p: Float, c: SceneColors) {
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

internal val STARS = List(36) { i ->
    val r = java.util.Random(i * 7919L)
    Triple(r.nextFloat(), r.nextFloat() * 0.6f, r.nextFloat() * 6f)
}

internal fun DrawScope.drawStars(p: Float, t: Float, color: Color) {
    STARS.forEach { (fx, fy, phase) ->
        val a = 0.55f + 0.45f * sin(t * 1.7f + phase)
        val sx = floor(fx * size.width / p) * p
        val sy = floor(fy * size.height / p) * p
        drawRect(color.copy(alpha = a.coerceIn(0f, 1f)), Offset(sx, sy), Size(p, p))
    }
}
