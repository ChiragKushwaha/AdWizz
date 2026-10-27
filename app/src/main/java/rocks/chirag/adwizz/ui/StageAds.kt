package rocks.chirag.adwizz.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.PI
import kotlin.math.sin

/** The fake ads on the monitor. */
private class FakeAd(val label: String, val background: Color, val text: Color)

private val ADS = listOf(
    FakeAd("AD", Color(0xFFE5534B), Color.White),
    FakeAd("SALE", Color(0xFFF4C95D), Color(0xFF3A1215)),
    FakeAd("50%", Color(0xFFE879B9), Color.White),
    FakeAd("BUY!", Color(0xFF4CAF7A), Color.White),
)

private val PUFF = listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f, -0.7f to -0.7f, 0.7f to 0.7f)

/**
 * The ads in a 2x2 grid on the screen. During the spell each one, a little after the previous,
 * lifts and wobbles, then shoots up past the top of the screen and vanishes in a puff of cloud.
 */
internal fun DrawScope.drawFlyingAds(s: StageScene) = with(s.layout) {
    val gap = 1.5f * u
    val tileW = (scrW - 3 * gap) / 2
    val tileH = (scrH - 3 * gap) / 2
    ADS.forEachIndexed { i, ad ->
        val start = 0.42f + i * 0.07f
        val p = seg(s.spell, start, start + 0.36f)
        if (p >= 1f) return@forEachIndexed
        val homeX = scrX + gap + (i % 2) * (tileW + gap)
        val homeY = scrY + gap + (i / 2) * (tileH + gap)
        val lift = seg(p, 0f, 0.25f)
        val fly = easeIn(seg(p, 0.25f, 1f))
        val cx = homeX + tileW / 2 + sin(p * PI.toFloat() * 3 + i) * 5 * u * lift
        val cy = homeY + tileH / 2 - lift * 4 * u - fly * (homeY + s.stageTop + tileH + 60 * u)
        val alpha = 1f - seg(p, 0.6f, 0.95f)
        // Ads love to blink while they sit on the screen.
        val blink = if (p == 0f && !s.reversing) 0.75f + 0.25f * ((sin(s.t * 4 + i * 1.3f) + 1) / 2) else 1f
        translate(cx, cy) {
            rotate(sin(p * 10 + i) * 14f * lift, Offset.Zero) {
                scale(1f - 0.3f * fly, Offset.Zero) { drawAdTile(s, ad, tileW, tileH, alpha, blink) }
            }
        }
        val puff = seg(p, 0.62f, 0.98f)
        if (puff > 0f && !s.reversing) {
            val r = (2 + puff * 6) * u
            PUFF.forEach { (ox, oy) -> dot(s.art.scene.cloud.copy(alpha = (1 - puff) * 0.9f), cx + ox * r - u, cy + oy * r - u, 2 * u) }
        }
    }
}

/** One ad tile centred on the origin. */
private fun DrawScope.drawAdTile(s: StageScene, ad: FakeAd, w: Float, h: Float, alpha: Float, blink: Float) {
    val topLeft = Offset(-w / 2, -h / 2)
    drawRect(ad.background.copy(alpha = alpha * blink), topLeft, Size(w, h))
    frame(topLeft, Size(w, h), s.ink.copy(alpha = alpha), s.u * 0.4f)
    val style = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = (h * 0.36f).toSp(), color = ad.text)
    val text = s.art.measurer.measure(ad.label, style)
    drawText(text, topLeft = Offset(-text.size.width / 2f, -text.size.height / 2f), alpha = alpha)
}
