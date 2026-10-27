package rocks.chirag.adshield.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rocks.chirag.adshield.data.Status
import kotlin.math.PI
import kotlin.math.sin

/**
 * The incantation the wizard shouts when protection turns on.
 *
 * "Wingardium Leviosa" comes from the Harry Potter books (Warner Bros. / J.K. Rowling). A short
 * phrase is fine for a personal build; for a Play Store release consider an original one, e.g.
 * "Adsus Vanishium!", since app stores act on trademark complaints.
 */
const val SPELL = "Wingardium Leviosa!"

/** Total length of the spell animation. */
const val SPELL_MS = 3200

/** The fake ads on the monitor: label, tile colour, text colour. */
private val ADS = listOf(
    Triple("AD", Color(0xFFE5534B), Color.White),
    Triple("SALE", Color(0xFFF4C95D), Color(0xFF3A1215)),
    Triple("50%", Color(0xFFE879B9), Color.White),
    Triple("BUY!", Color(0xFF4CAF7A), Color.White),
)

/** 0..1 progress of [v] between [a] and [b]. */
private fun seg(v: Float, a: Float, b: Float) = ((v - a) / (b - a)).coerceIn(0f, 1f)
private fun easeIn(x: Float) = x * x * x
private fun easeOut(x: Float) = 1 - (1 - x) * (1 - x)

/**
 * Wizard on a broom next to a monitor full of ads. When protection turns on he casts the spell:
 * the ads levitate off the screen, float up out of view and vanish in a puff; the screen goes
 * clean. Turning protection off brings the ads tumbling back.
 *
 * Everything is drawn in one Canvas; animation values are read in the draw phase only.
 */
@Composable
fun WizardStage(status: Status, modifier: Modifier = Modifier) {
    val protectedNow = status == Status.RUNNING || status == Status.STARTING
    // 0 = ads on screen, 1 = spell finished. Starts at its resting value: no replay on rotation.
    val spell = remember { Animatable(if (protectedNow) 1f else 0f) }
    var reversing by remember { mutableStateOf(false) }
    LaunchedEffect(protectedNow) {
        if (protectedNow && spell.value < 1f) {
            reversing = false
            spell.snapTo(0f)
            spell.animateTo(1f, tween(SPELL_MS, easing = LinearEasing))
        } else if (!protectedNow && spell.value > 0f) {
            reversing = true
            spell.animateTo(0f, tween(700, easing = FastOutSlowInEasing))
            reversing = false
        }
    }

    val clock = rememberSceneClock()
    val measurer = rememberTextMeasurer()
    val idleSprite = remember { spriteBitmap(WizardArt.IDLE) }
    val owl = remember { spriteBitmap(DesignArt.OWL, DesignArt.OWL_PALETTE) }
    val castSprite = remember { spriteBitmap(WizardArt.CASTING) }
    val scheme = MaterialTheme.colorScheme
    val scene = sceneColors()
    var stageTop by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier.fillMaxWidth().height(210.dp)
            .onGloballyPositioned { stageTop = it.boundsInRoot().top }
            .testTag("wizardStage")
            .semantics {
                contentDescription = if (protectedNow) "Wizard has cleared the ads from the screen"
                else "Screen full of ads"
            }
    ) {
        val t = clock.value
        val s = spell.value
        val u = minOf(size.width / 90f, size.height / 52f)   // layout unit
        val ink = scene.ink

        // ---------- monitor ----------
        val monX = 4 * u; val monY = 14 * u; val monW = 42 * u; val monH = 31 * u
        val inset = 3 * u
        val scrX = monX + inset; val scrY = monY + inset; val scrW = monW - 2 * inset; val scrH = monH - 2 * inset
        drawRect(ink, Offset(monX + u, monY + u), Size(monW, monH))                      // shadow
        drawRect(Color(0xFFC9CCD6), Offset(monX, monY), Size(monW, monH))
        frame(Offset(monX, monY), Size(monW, monH), ink, u * 0.6f)
        drawRect(Color(0xFFC9CCD6), Offset(monX + monW / 2 - 4 * u, monY + monH), Size(8 * u, 4 * u))
        drawRect(Color(0xFFC9CCD6), Offset(monX + monW / 2 - 11 * u, monY + monH + 4 * u), Size(22 * u, 2 * u))
        frame(Offset(monX + monW / 2 - 11 * u, monY + monH + 4 * u), Size(22 * u, 2 * u), ink, u * 0.5f)
        drawRect(Color(0xFF1C2340), Offset(scrX, scrY), Size(scrW, scrH))
        drawRect(Color(0xFF4CAF7A), Offset(monX + monW - 5 * u, monY + monH - 2 * u), Size(1.5f * u, u))  // power LED

        // Snowy owl from the design, perched on the monitor. Blinks now and then; hops when the
        // spell goes off.
        val oc = 0.78f * u
        val owlW = DesignArt.OWL[0].length * oc; val owlH = DesignArt.OWL.size * oc
        val hop = if (reversing) 0f else sin(seg(s, 0.38f, 0.58f) * PI.toFloat()) * 4 * u
        val owlX = monX + monW - owlW - 3 * u; val owlY = monY - owlH + 1.2f * oc - hop
        drawImage(owl, dstOffset = IntOffset(owlX.toInt(), owlY.toInt()), dstSize = IntSize(owlW.toInt(), owlH.toInt()), filterQuality = FilterQuality.None)
        if ((t % 4.3f) < 0.15f) {   // blink: cover the yellow eyes with feathers for a moment
            DesignArt.OWL.forEachIndexed { r, row -> row.forEachIndexed { c, ch ->
                if (ch == 'Y') drawRect(Color(0xFFA4A4A8), Offset(owlX + c * oc, owlY + r * oc), Size(oc + 0.5f, oc + 0.5f))
            } }
        }

        // Clean screen once the ads are gone.
        val clean = seg(s, 0.8f, 1f)
        if (clean > 0f && !reversing) {
            val a = clean
            val check = listOf(0 to 2, 1 to 3, 2 to 4, 3 to 3, 4 to 2, 5 to 1, 6 to 0)
            val cx = scrX + scrW / 2 - 3.5f * u * 1.2f; val cy = scrY + scrH * 0.22f
            check.forEach { (x, y) ->
                drawRect(Color(0xFF4CAF7A).copy(alpha = a), Offset(cx + x * 1.2f * u, cy + y * 1.2f * u), Size(1.2f * u, 1.2f * u))
                drawRect(Color(0xFF4CAF7A).copy(alpha = a), Offset(cx + x * 1.2f * u, cy + (y + 1) * 1.2f * u), Size(1.2f * u, 1.2f * u))
            }
            val label = measurer.measure("NO ADS", TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = (3.2f * u).toSp(), color = Color(0xFFF4F1EA)))
            drawText(label, topLeft = Offset(scrX + (scrW - label.size.width) / 2, scrY + scrH * 0.62f), alpha = a)
            // A few twinkles.
            repeat(3) { k ->
                val tw = (sin(t * 3 + k * 2.1f) + 1) / 2
                drawRect(scene.star.copy(alpha = a * tw), Offset(scrX + scrW * (0.15f + k * 0.33f), scrY + scrH * (0.12f + (k % 2) * 0.7f)), Size(u, u))
            }
        }

        // ---------- ads ----------
        val gap = 1.5f * u
        val tileW = (scrW - 3 * gap) / 2; val tileH = (scrH - 3 * gap) / 2
        ADS.forEachIndexed { i, (label, bg, fg) ->
            val homeX = scrX + gap + (i % 2) * (tileW + gap)
            val homeY = scrY + gap + (i / 2) * (tileH + gap)
            val start = 0.42f + i * 0.07f
            val p = seg(s, start, start + 0.36f)
            if (p >= 1f) return@forEachIndexed
            // Levitate: lift a little and wobble, then shoot up past the top of the screen.
            val lift = seg(p, 0f, 0.25f)
            val fly = easeIn(seg(p, 0.25f, 1f))
            val dy = -lift * 4 * u - fly * (homeY + stageTop + tileH + 60 * u)
            val dx = sin(p * PI.toFloat() * 3 + i) * 5 * u * lift
            val rot = sin(p * 10 + i) * 14f * lift
            val alpha = 1f - seg(p, 0.6f, 0.95f)
            // Blink while sitting on the screen (ads love to blink).
            val blink = if (p == 0f && !reversing) 0.75f + 0.25f * ((sin(t * 4 + i * 1.3f) + 1) / 2) else 1f
            val cx = homeX + tileW / 2 + dx; val cy = homeY + tileH / 2 + dy
            translate(cx, cy) {
                rotate(rot, Offset.Zero) {
                    scale(1f - 0.3f * fly, Offset.Zero) {
                        drawRect(bg.copy(alpha = alpha * blink), Offset(-tileW / 2, -tileH / 2), Size(tileW, tileH))
                        frame(Offset(-tileW / 2, -tileH / 2), Size(tileW, tileH), ink.copy(alpha = alpha), u * 0.4f)
                        val tl = measurer.measure(label, TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = (tileH * 0.36f).toSp(), color = fg))
                        drawText(tl, topLeft = Offset(-tl.size.width / 2f, -tl.size.height / 2f), alpha = alpha)
                    }
                }
            }
            // Puff of cloud where the ad vanishes into the sky.
            val puff = seg(p, 0.62f, 0.98f)
            if (puff > 0f && !reversing) {
                val r = (2 + puff * 6) * u
                val pa = (1 - puff) * 0.9f
                listOf(-1f to 0f, 1f to 0f, 0f to -1f, 0f to 1f, -0.7f to -0.7f, 0.7f to 0.7f).forEach { (ox, oy) ->
                    drawRect(scene.cloud.copy(alpha = pa), Offset(cx + ox * r - u, cy + oy * r - u), Size(2 * u, 2 * u))
                }
            }
        }

        // ---------- wizard ----------
        val cell = 0.7f * u
        val casting = !reversing && s > 0.02f && s < 0.8f
        val sprite = if (casting) castSprite else idleSprite
        val bob = sin(t * 2.2f) * 1.2f * u
        val wizW = WizardArt.WIDTH * cell; val wizH = WizardArt.HEIGHT * cell
        val wizX = size.width - wizW - 3 * u
        val wizY = 13 * u + bob
        // Flying pose (the broom is already angled in the art): sway gently.
        val tilt = sin(t * 1.3f) * 2.5f
        val pivot = Offset(wizX + wizW / 2, wizY + wizH / 2)
        rotate(tilt, pivot) {
            drawImage(
                sprite, dstOffset = IntOffset(wizX.toInt(), wizY.toInt()),
                dstSize = IntSize(wizW.toInt(), wizH.toInt()),
                filterQuality = FilterQuality.None,   // crisp pixels, no blur
            )
        }

        // Spell sparkles from the wand tip (rotated with the sprite) to the monitor.
        val rad = Math.toRadians(tilt.toDouble())
        val lx = wizX + (WizardArt.WAND_X + 0.5f) * cell - pivot.x
        val ly = wizY + (WizardArt.WAND_Y + 0.5f) * cell - pivot.y
        val tipX = pivot.x + (lx * kotlin.math.cos(rad) - ly * kotlin.math.sin(rad)).toFloat()
        val tipY = pivot.y + (lx * kotlin.math.sin(rad) + ly * kotlin.math.cos(rad)).toFloat()
        if (casting) {
            val glow = (sin(t * 12) + 1) / 2
            drawRect(scene.star.copy(alpha = 0.5f + 0.5f * glow), Offset(tipX - 2 * u, tipY - 2 * u), Size(u, u))
            drawRect(scene.star.copy(alpha = 0.5f + 0.5f * (1 - glow)), Offset(tipX + 1.5f * u, tipY - 2.5f * u), Size(u, u))
        }
        val trail = seg(s, 0.22f, 0.55f)
        if (trail > 0f && trail < 1f && !reversing) {
            val endX = scrX + scrW / 2; val endY = scrY + scrH / 2
            repeat(7) { k ->
                val q = (trail * 1.4f - k * 0.06f).coerceIn(0f, 1f)
                if (q <= 0f || q >= 1f) return@repeat
                val x = tipX + (endX - tipX) * q
                val y = tipY + (endY - tipY) * q - sin(q * PI.toFloat()) * 18 * u   // arc over the gap
                val sz = (1.6f - k * 0.15f) * u
                drawRect(if (k % 2 == 0) scene.star else Color.White, Offset(x - sz / 2, y - sz / 2), Size(sz, sz))
            }
        }

        // Speech bubble with the incantation, typed out letter by letter.
        val typed = seg(s, 0.04f, 0.32f)
        val bubbleAlpha = if (reversing) 0f else (if (s < 0.75f) 1f else 1f - seg(s, 0.75f, 0.85f))
        if (typed > 0f && bubbleAlpha > 0f) {
            val shown = SPELL.take((SPELL.length * typed).toInt().coerceAtLeast(1))
            val style = TextStyle(fontFamily = PixelFont, fontWeight = FontWeight.Bold, fontSize = (3.4f * u).toSp(), color = scheme.onSurface)
            val full = measurer.measure(SPELL, style)
            val txt = measurer.measure(shown, style)
            val bw = full.size.width + 4 * u; val bh = full.size.height + 2.5f * u
            val bx = (size.width - bw - 2 * u).coerceAtMost(wizX + 6 * u); val by = 1.5f * u
            drawRect(ink.copy(alpha = bubbleAlpha), Offset(bx + u * 0.8f, by + u * 0.8f), Size(bw, bh))
            drawRect(scheme.surface.copy(alpha = bubbleAlpha), Offset(bx, by), Size(bw, bh))
            frame(Offset(bx, by), Size(bw, bh), ink.copy(alpha = bubbleAlpha), u * 0.5f)
            // Tail pointing at the wizard.
            drawRect(scheme.surface.copy(alpha = bubbleAlpha), Offset(wizX + 22 * cell, by + bh), Size(2 * u, 1.5f * u))
            drawText(txt, topLeft = Offset(bx + 2 * u, by + 1.25f * u), alpha = bubbleAlpha)
        }
    }
}

/** Paints a pixel-art grid into a tiny bitmap (1 px per cell) so it can be drawn in one call. */
fun spriteBitmap(rows: Array<String>, palette: Map<Char, Color> = WizardArt.palette): ImageBitmap {
    val bmp = createBitmap(rows[0].length, rows.size)
    rows.forEachIndexed { y, row ->
        row.forEachIndexed { x, ch -> palette[ch]?.let { bmp[x, y] = it.toArgb() } }
    }
    return bmp.asImageBitmap()
}

/** Draws a pixel-art grid cell by cell; '.' cells are transparent. */
fun DrawScope.drawSprite(rows: Array<String>, x: Float, y: Float, cell: Float) {
    rows.forEachIndexed { r, row ->
        row.forEachIndexed { c, ch ->
            if (ch != '.') {
                val color = WizardArt.palette[ch] ?: return@forEachIndexed
                // +0.5px overlap hides hairline seams on fractional densities.
                drawRect(color, Offset(x + c * cell, y + r * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

private fun DrawScope.frame(topLeft: Offset, size: Size, color: Color, w: Float) {
    drawRect(color, topLeft, Size(size.width, w))
    drawRect(color, Offset(topLeft.x, topLeft.y + size.height - w), Size(size.width, w))
    drawRect(color, topLeft, Size(w, size.height))
    drawRect(color, Offset(topLeft.x + size.width - w, topLeft.y), Size(w, size.height))
}
