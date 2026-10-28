package rocks.chirag.adwizz.ui

import rocks.chirag.adwizz.tr
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * How to switch the Reels Blocker add-on on. Android 13+ locks Accessibility for apps installed
 * outside an app store ("Restricted setting") until the user allows it in the app's App info, and
 * that option only shows up after one try. Older Android versions just need the switch.
 */
internal fun needsRestrictedSettingsSteps(sdk: Int = Build.VERSION.SDK_INT) = sdk >= 33

@Composable
internal fun ReelsSetupGuide(
    onOpenAccessibility: () -> Unit,
    onOpenAddonInfo: () -> Unit,
    restricted: Boolean = needsRestrictedSettingsSteps(),
) {
    Column(Modifier.fillMaxWidth().testTag("reelsGuide"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("guide.title"), fontWeight = FontWeight.SemiBold)
        if (restricted) {
            GuideStep(
                1, tr("guide.step1"),
                tr("guide.open_a11y"), onOpenAccessibility,
            ) { MockRestricted() }
            GuideStep(
                2, tr("guide.step2"),
                tr("guide.open_info"), onOpenAddonInfo,
            ) { MockAllowRestricted() }
            GuideStep(
                3, tr("guide.step3"),
                tr("guide.open_a11y"), onOpenAccessibility,
            ) { MockTurnOn() }
        } else {
            GuideStep(1, tr("guide.step_simple"), tr("guide.open_a11y"), onOpenAccessibility) { MockTurnOn() }
        }
    }
}

@Composable
private fun GuideStep(number: Int, text: String, button: String, onClick: () -> Unit, art: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("guideStep$number")) {
        Text("$number. $text", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(6.dp))
        art()
        TextButton(onClick = onClick, modifier = Modifier.testTag("guideButton$number")) { Text(button) }
    }
}
