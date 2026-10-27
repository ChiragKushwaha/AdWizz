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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import rocks.chirag.adshield.data.Status

/**
 * The incantation the wizard shouts when protection turns on.
 *
 * "Wingardium Leviosa" comes from the Harry Potter books (Warner Bros. / J.K. Rowling). A short
 * phrase is fine for a personal build; for a store release consider an original one, e.g.
 * "Adsus Vanishium!", since stores act on trademark complaints.
 */
const val SPELL = "Wingardium Leviosa!"

/** Total length of the spell animation. */
const val SPELL_MS = 3200

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
    val art = rememberStageArt(rememberTextMeasurer(), MaterialTheme.colorScheme, sceneColors())
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
        val scene = StageScene(StageLayout(size.width, size.height), clock.value, spell.value, reversing, stageTop, art)
        drawMonitor(scene)
        drawOwl(scene)
        drawCleanScreen(scene)
        drawFlyingAds(scene)
        val wand = drawWizard(scene)
        drawSparkles(scene, wand)
        drawSpellBubble(scene)
    }
}
