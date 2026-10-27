package rocks.chirag.adwizz.ui

import android.provider.Settings as AndroidSettings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot
import kotlin.math.max

/** Ask for a theme change that spreads out as a circle from [origin] (root coordinates). */
class ThemeRevealController {
    internal val requests = Channel<Pair<ThemeMode, Offset>>(Channel.CONFLATED)
    fun switchTo(mode: ThemeMode, origin: Offset) {
        requests.trySend(mode to origin)
    }
}

val LocalThemeReveal = staticCompositionLocalOf<ThemeRevealController?> { null }

/** How long the circle takes to cover the screen. */
const val THEME_REVEAL_MS = 550

/**
 * Wraps the app. On a theme switch it snapshots the current (old-theme) screen, applies the new
 * theme underneath, and draws the snapshot on top with a growing hole cut out of it, so the new
 * theme appears to spread outward from the button that was tapped.
 *
 * Falls back to an instant switch if the snapshot fails or the user turned animations off.
 */
@Composable
fun ThemeRevealHost(onApply: (ThemeMode) -> Unit, content: @Composable () -> Unit) {
    val controller = remember { ThemeRevealController() }
    val layer = rememberGraphicsLayer()
    val radius = remember { Animatable(0f) }
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val context = LocalContext.current

    LaunchedEffect(controller) {
        for ((mode, at) in controller.requests) {
            val animationsOff = runCatching {
                AndroidSettings.Global.getFloat(context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }.getOrDefault(false)
            val shot = if (animationsOff || size == IntSize.Zero) null
            else withTimeoutOrNull(300) { runCatching { layer.toImageBitmap() }.getOrNull() }

            if (shot == null) {
                onApply(mode)
                continue
            }
            origin = at
            radius.snapTo(0f)
            snapshot = shot
            onApply(mode)
            // Far enough to reach the furthest corner from the tap point.
            val reach = hypot(max(at.x, size.width - at.x), max(at.y, size.height - at.y))
            radius.animateTo(reach, tween(THEME_REVEAL_MS, easing = FastOutSlowInEasing))
            snapshot = null
        }
    }

    CompositionLocalProvider(LocalThemeReveal provides controller) {
        Box(Modifier.fillMaxSize().onSizeChanged { size = it }) {
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }
            ) { content() }

            snapshot?.let { old ->
                Canvas(Modifier.fillMaxSize()) {
                    val hole = Path().apply { addOval(Rect(origin, radius.value)) }
                    clipPath(hole, ClipOp.Difference) { drawImage(old) }
                }
            }
        }
    }
}
