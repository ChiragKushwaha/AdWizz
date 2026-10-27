package rocks.chirag.adwizz.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.PI
import kotlin.math.sin

private val Bezel = Color(0xFFC9CCD6)
private val Screen = Color(0xFF1C2340)
private val Green = Color(0xFF4CAF7A)
private val OwlShade = Color(0xFFA4A4A8)

/** Pixel CRT monitor: shadow, bezel, stand, dark screen and a green power LED. */
internal fun DrawScope.drawMonitor(s: StageScene) = with(s.layout) {
    drawRect(s.ink, Offset(monX + u, monY + u), Size(monW, monH))          // hard shadow
    drawRect(Bezel, Offset(monX, monY), Size(monW, monH))
    frame(Offset(monX, monY), Size(monW, monH), s.ink, u * 0.6f)
    drawRect(Bezel, Offset(monX + monW / 2 - 4 * u, monY + monH), Size(8 * u, 4 * u))
    val base = Offset(monX + monW / 2 - 11 * u, monY + monH + 4 * u)
    drawRect(Bezel, base, Size(22 * u, 2 * u))
    frame(base, Size(22 * u, 2 * u), s.ink, u * 0.5f)
    drawRect(Screen, Offset(scrX, scrY), Size(scrW, scrH))
    drawRect(Green, Offset(monX + monW - 5 * u, monY + monH - 2 * u), Size(1.5f * u, u))
}

/** The design's snowy owl perched on the monitor: blinks now and then, hops when the spell fires. */
internal fun DrawScope.drawOwl(s: StageScene) = with(s.layout) {
    val oc = 0.78f * u
    val w = DesignArt.OWL[0].length * oc
    val h = DesignArt.OWL.size * oc
    val hop = if (s.reversing) 0f else sin(seg(s.spell, 0.38f, 0.58f) * PI.toFloat()) * 4 * u
    val x = monX + monW - w - 3 * u
    val y = monY - h + 1.2f * oc - hop
    drawPixels(s.art.owl, x, y, w, h)
    if (s.t % 4.3f < 0.15f) {   // blink: cover the yellow eyes with feathers for a moment
        DesignArt.OWL.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch -> if (ch == 'Y') dot(OwlShade, x + c * oc, y + r * oc, oc + 0.5f) }
        }
    }
}

/** Once the ads are gone: a green pixel tick, "NO ADS" and a few twinkles. */
internal fun DrawScope.drawCleanScreen(s: StageScene) = with(s.layout) {
    val a = seg(s.spell, 0.8f, 1f)
    if (a <= 0f || s.reversing) return@with
    val px = 1.2f * u
    val cx = scrX + scrW / 2 - 3.5f * px
    val cy = scrY + scrH * 0.22f
    listOf(0 to 2, 1 to 3, 2 to 4, 3 to 3, 4 to 2, 5 to 1, 6 to 0).forEach { (x, y) ->
        dot(Green.copy(alpha = a), cx + x * px, cy + y * px, px)
        dot(Green.copy(alpha = a), cx + x * px, cy + (y + 1) * px, px)
    }
    val style = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = (3.2f * u).toSp(), color = Color(0xFFF4F1EA))
    val label = s.art.measurer.measure("NO ADS", style)
    drawText(label, topLeft = Offset(scrX + (scrW - label.size.width) / 2, scrY + scrH * 0.62f), alpha = a)
    repeat(3) { k ->
        val twinkle = (sin(s.t * 3 + k * 2.1f) + 1) / 2
        dot(s.art.scene.star.copy(alpha = a * twinkle), scrX + scrW * (0.15f + k * 0.33f), scrY + scrH * (0.12f + (k % 2) * 0.7f), u)
    }
}
