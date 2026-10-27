package rocks.chirag.adshield.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Draws the wizard bobbing and swaying on his broom. Returns where his wand tip is now. */
internal fun DrawScope.drawWizard(s: StageScene): Offset = with(s.layout) {
    val y = 13 * u + sin(s.t * 2.2f) * 1.2f * u
    val tilt = sin(s.t * 1.3f) * 2.5f
    val pivot = Offset(wizX + wizW / 2, y + wizH / 2)
    rotate(tilt, pivot) { drawPixels(if (s.casting) s.art.casting else s.art.idle, wizX, y, wizW, wizH) }
    // The wand tip, rotated along with the sprite.
    val rad = Math.toRadians(tilt.toDouble())
    val lx = wizX + (WizardArt.WAND_X + 0.5f) * cell - pivot.x
    val ly = y + (WizardArt.WAND_Y + 0.5f) * cell - pivot.y
    Offset(pivot.x + (lx * cos(rad) - ly * sin(rad)).toFloat(), pivot.y + (lx * sin(rad) + ly * cos(rad)).toFloat())
}

/** Glow on the wand while casting, then a trail of sparkles arcing over to the monitor. */
internal fun DrawScope.drawSparkles(s: StageScene, tip: Offset) = with(s.layout) {
    val star = s.art.scene.star
    if (s.casting) {
        val glow = (sin(s.t * 12) + 1) / 2
        dot(star.copy(alpha = 0.5f + 0.5f * glow), tip.x - 2 * u, tip.y - 2 * u, u)
        dot(star.copy(alpha = 0.5f + 0.5f * (1 - glow)), tip.x + 1.5f * u, tip.y - 2.5f * u, u)
    }
    val trail = seg(s.spell, 0.22f, 0.55f)
    if (trail <= 0f || trail >= 1f || s.reversing) return@with
    val end = Offset(scrX + scrW / 2, scrY + scrH / 2)
    repeat(7) { k ->
        val q = (trail * 1.4f - k * 0.06f).coerceIn(0f, 1f)
        if (q <= 0f || q >= 1f) return@repeat
        val x = tip.x + (end.x - tip.x) * q
        val y = tip.y + (end.y - tip.y) * q - sin(q * PI.toFloat()) * 18 * u
        val size = (1.6f - k * 0.15f) * u
        dot(if (k % 2 == 0) star else Color.White, x - size / 2, y - size / 2, size)
    }
}

/** Speech bubble with the incantation, typed out letter by letter, fading once the ads are gone. */
internal fun DrawScope.drawSpellBubble(s: StageScene) = with(s.layout) {
    val typed = seg(s.spell, 0.04f, 0.32f)
    val alpha = if (s.reversing) 0f else 1f - seg(s.spell, 0.75f, 0.85f)
    if (typed <= 0f || alpha <= 0f) return@with
    val scheme = s.art.scheme
    val style = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = (3.4f * u).toSp(), color = scheme.onSurface)
    val full = s.art.measurer.measure(SPELL, style)
    val shown = s.art.measurer.measure(SPELL.take((SPELL.length * typed).toInt().coerceAtLeast(1)), style)
    val w = full.size.width + 4 * u
    val h = full.size.height + 2.5f * u
    val x = (width - w - 2 * u).coerceAtMost(wizX + 6 * u)
    val y = 1.5f * u
    drawRect(s.ink.copy(alpha = alpha), Offset(x + u * 0.8f, y + u * 0.8f), Size(w, h))
    drawRect(scheme.surface.copy(alpha = alpha), Offset(x, y), Size(w, h))
    frame(Offset(x, y), Size(w, h), s.ink.copy(alpha = alpha), u * 0.5f)
    drawRect(scheme.surface.copy(alpha = alpha), Offset(wizX + 22 * cell, y + h), Size(2 * u, 1.5f * u))   // tail
    drawText(shown, topLeft = Offset(x + 2 * u, y + 1.25f * u), alpha = alpha)
}
