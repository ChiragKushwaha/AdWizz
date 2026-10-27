package rocks.chirag.adwizz.ui

import android.content.Context
import android.provider.Settings as AndroidSettings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Ambient animations (drifting clouds, bobbing wizard, blinking ads). Off when the user turned
 * system animations off, and in UI tests (endless animations never let the test clock go idle).
 */
object Motion {
    @Volatile var ambientEnabled = true
}

@Composable
fun ambientMotion(): Boolean {
    val context = LocalContext.current
    return remember { Motion.ambientEnabled && systemAnimationsOn(context) }
}

fun systemAnimationsOn(context: Context): Boolean = runCatching {
    AndroidSettings.Global.getFloat(context.contentResolver, AndroidSettings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
}.getOrDefault(true)
