package rocks.chirag.adshield.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextMeasurer

/** Where everything sits on the stage, in a layout unit [u] that scales with the stage size. */
class StageLayout(val width: Float, height: Float) {
    val u = minOf(width / 90f, height / 52f)

    val monX = 4 * u
    val monY = 14 * u
    val monW = 42 * u
    val monH = 31 * u
    private val inset = 3 * u
    val scrX = monX + inset
    val scrY = monY + inset
    val scrW = monW - 2 * inset
    val scrH = monH - 2 * inset

    /** Size of one wizard sprite cell. */
    val cell = 0.7f * u
    val wizW = WizardArt.WIDTH * cell
    val wizH = WizardArt.HEIGHT * cell
    val wizX = width - wizW - 3 * u
}

/** Bitmaps, text measurer and colours the stage draws with (created once per composition). */
class StageArt(
    val measurer: TextMeasurer,
    val scheme: ColorScheme,
    val scene: SceneColors,
    val idle: ImageBitmap,
    val casting: ImageBitmap,
    val owl: ImageBitmap,
)

@Composable
internal fun rememberStageArt(measurer: TextMeasurer, scheme: ColorScheme, scene: SceneColors): StageArt {
    val idle = remember { spriteBitmap(WizardArt.IDLE) }
    val casting = remember { spriteBitmap(WizardArt.CASTING) }
    val owl = remember { spriteBitmap(DesignArt.OWL, DesignArt.OWL_PALETTE) }
    return StageArt(measurer, scheme, scene, idle, casting, owl)
}

/**
 * Everything one frame of the stage needs.
 * @param t seconds of ambient time (bobbing, blinking)
 * @param spell 0 = ads on screen … 1 = spell finished
 * @param reversing true while the ads tumble back after protection was turned off
 * @param stageTop stage's top in window coordinates, so ads can fly past the top of the screen
 */
class StageScene(
    val layout: StageLayout,
    val t: Float,
    val spell: Float,
    val reversing: Boolean,
    val stageTop: Float,
    val art: StageArt,
) {
    val u get() = layout.u
    val ink get() = art.scene.ink

    /** The wizard holds his wand up during the spell. */
    val casting get() = !reversing && spell > 0.02f && spell < 0.8f
}
