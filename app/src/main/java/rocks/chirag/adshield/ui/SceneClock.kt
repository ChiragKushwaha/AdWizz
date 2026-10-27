package rocks.chirag.adshield.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*

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
