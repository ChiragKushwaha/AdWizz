package rocks.chirag.adshield.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "System"), LIGHT("light", "Light"), DARK("dark", "Dark");

    companion object {
        fun from(key: String) = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** Current theme choice; MainActivity loads it from Settings, the Settings tab updates both. */
object ThemeState {
    val mode = MutableStateFlow(ThemeMode.SYSTEM)
}

/** True when the app should currently render dark. */
@Composable
fun isAppInDarkTheme(): Boolean {
    val mode by ThemeState.mode.collectAsStateWithLifecycle()
    return when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
}

@Composable
fun sceneColors(): SceneColors = if (isAppInDarkTheme()) DarkScene else LightScene

@Composable
fun AdShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isAppInDarkTheme()) DarkColors else LightColors,
        typography = PixelTypography,
        shapes = PixelShapes,
        content = content,
    )
}
