package rocks.chirag.adwizz.ui

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
        Text("Turn on the Reels Blocker", fontWeight = FontWeight.SemiBold)
        if (restricted) {
            GuideStep(
                1, "Open Accessibility and tap \"AdWizz Reels Blocker\". Android says \"Restricted setting\": tap OK. " +
                    "(On Samsung it's under Installed apps.)",
                "Open Accessibility", onOpenAccessibility,
            ) { MockRestricted() }
            GuideStep(
                2, "Open the add-on's App info, tap ⋮ at the top right, then \"Allow restricted settings\" " +
                    "and confirm with your PIN or fingerprint.",
                "Open add-on App info", onOpenAddonInfo,
            ) { MockAllowRestricted() }
            GuideStep(
                3, "Go back to Accessibility and turn on \"AdWizz Reels Blocker\".",
                "Open Accessibility", onOpenAccessibility,
            ) { MockTurnOn() }
        } else {
            GuideStep(1, "Open Accessibility and turn on \"AdWizz Reels Blocker\".", "Open Accessibility", onOpenAccessibility) { MockTurnOn() }
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
