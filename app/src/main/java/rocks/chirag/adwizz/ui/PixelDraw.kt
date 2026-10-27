package rocks.chirag.adwizz.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set

/** 0..1 progress of [v] between [a] and [b]. */
internal fun seg(v: Float, a: Float, b: Float) = ((v - a) / (b - a)).coerceIn(0f, 1f)

internal fun easeIn(x: Float) = x * x * x

/** Paints a pixel-art grid into a tiny bitmap (1 px per cell) so it can be drawn in one call. */
fun spriteBitmap(rows: Array<String>, palette: Map<Char, Color> = WizardArt.palette): ImageBitmap {
    val bmp = createBitmap(rows[0].length, rows.size)
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, ch -> palette[ch]?.let { bmp[x, y] = it.toArgb() } }
    }
    return bmp.asImageBitmap()
}

/** Draws a sprite bitmap scaled up with hard pixel edges (no blur). */
internal fun DrawScope.drawPixels(image: ImageBitmap, x: Float, y: Float, w: Float, h: Float, alpha: Float = 1f) {
    drawImage(
        image, dstOffset = IntOffset(x.toInt(), y.toInt()), dstSize = IntSize(w.toInt(), h.toInt()),
        alpha = alpha, filterQuality = FilterQuality.None,
    )
}

/** Rectangle outline of width [w] drawn inside [topLeft]/[size]. */
internal fun DrawScope.frame(topLeft: Offset, size: Size, color: Color, w: Float) {
    drawRect(color, topLeft, Size(size.width, w))
    drawRect(color, Offset(topLeft.x, topLeft.y + size.height - w), Size(size.width, w))
    drawRect(color, topLeft, Size(w, size.height))
    drawRect(color, Offset(topLeft.x + size.width - w, topLeft.y), Size(w, size.height))
}

/** One square "pixel" of side [s] at ([x], [y]). */
internal fun DrawScope.dot(color: Color, x: Float, y: Float, s: Float) = drawRect(color, Offset(x, y), Size(s, s))
