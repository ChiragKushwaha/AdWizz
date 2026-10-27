package rocks.chirag.adshield.ui

import android.content.Context
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import rocks.chirag.adshield.R

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

/** Extra colours for the pixel scene that Material's scheme has no slot for. */
data class SceneColors(
    val sky: Color, val cloud: Color, val cloudShade: Color, val trees: Color, val treesFar: Color,
    val castle: Color, val castleRoof: Color, val ink: Color, val star: Color, val moon: Color,
)

val LightScene = SceneColors(
    sky = Color(0xFFDCE5EE), cloud = Color(0xFFF4F1EA), cloudShade = Color(0xFFE6E4E3),
    trees = Color(0xFF6B5878), treesFar = Color(0xFF9C8FAE), castle = Color(0xFFE9E7EE),
    castleRoof = Color(0xFF7275C7), ink = Color(0xFF3A1215), star = Color(0xFFF4C95D), moon = Color(0xFFF4F1EA),
)

val DarkScene = SceneColors(
    sky = Color(0xFF121733), cloud = Color(0xFF262D58), cloudShade = Color(0xFF1D2347),
    trees = Color(0xFF241E38), treesFar = Color(0xFF35304F), castle = Color(0xFF3A3F70),
    castleRoof = Color(0xFF6E64B8), ink = Color(0xFF0A0D1F), star = Color(0xFFF4E3A1), moon = Color(0xFFF4E9C8),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF5E1A1D),          // deep maroon, like the title lettering
    onPrimary = Color(0xFFF4F1EA),
    primaryContainer = Color(0xFFEBD5D0),
    onPrimaryContainer = Color(0xFF3A1215),
    secondary = Color(0xFF6B5878),        // pine-tree purple
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3DBEC),
    onSecondaryContainer = Color(0xFF2D2236),
    tertiary = Color(0xFFB8862B),          // gold
    background = LightScene.sky,
    onBackground = Color(0xFF3A1215),
    surface = Color(0xFFF4F1EA),           // cloud cream panels
    onSurface = Color(0xFF3A1215),
    surfaceVariant = Color(0xFFE8E2D6),
    onSurfaceVariant = Color(0xFF6A4A4D),
    surfaceContainerLowest = Color(0xFFFFFCF5),
    surfaceContainerLow = Color(0xFFF7F3EB),
    surfaceContainer = Color(0xFFF1ECE2),
    surfaceContainerHigh = Color(0xFFEAE4D8),
    surfaceContainerHighest = Color(0xFFE3DCCF),
    outline = Color(0xFF3A1215),
    outlineVariant = Color(0xFFC9BDB2),
    error = Color(0xFFC0392B),
    onError = Color.White,
    errorContainer = Color(0xFFF8D7D2),
    onErrorContainer = Color(0xFF5E1A1D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF2C14E),           // moonlit gold
    onPrimary = Color(0xFF2A1A05),
    primaryContainer = Color(0xFF4A3A12),
    onPrimaryContainer = Color(0xFFF9E3A8),
    secondary = Color(0xFFA99BD6),
    onSecondary = Color(0xFF1B1533),
    secondaryContainer = Color(0xFF3B3466),
    onSecondaryContainer = Color(0xFFE6DFFF),
    tertiary = Color(0xFFF2C14E),
    background = DarkScene.sky,
    onBackground = Color(0xFFF1E9DA),
    surface = Color(0xFF1D2347),
    onSurface = Color(0xFFF1E9DA),
    surfaceVariant = Color(0xFF262D58),
    onSurfaceVariant = Color(0xFFB8B3CC),
    surfaceContainerLowest = Color(0xFF151A3A),
    surfaceContainerLow = Color(0xFF1A2042),
    surfaceContainer = Color(0xFF1F2649),
    surfaceContainerHigh = Color(0xFF252C52),
    surfaceContainerHighest = Color(0xFF2C335B),
    outline = Color(0xFF070914),
    outlineVariant = Color(0xFF3A416E),
    error = Color(0xFFFF7A6E),
    onError = Color(0xFF3B0904),
    errorContainer = Color(0xFF4F1A16),
    onErrorContainer = Color(0xFFFFD8D3),
)

@OptIn(ExperimentalTextApi::class)
val PixelFont = FontFamily(
    Font(R.font.pixelify_sans, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.pixelify_sans, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.pixelify_sans, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.pixelify_sans, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun TextStyle.pixel() = copy(fontFamily = PixelFont)

private val PixelTypography = Typography().run {
    copy(
        displayLarge = displayLarge.pixel(), displayMedium = displayMedium.pixel(), displaySmall = displaySmall.pixel(),
        headlineLarge = headlineLarge.pixel(), headlineMedium = headlineMedium.pixel(), headlineSmall = headlineSmall.pixel(),
        titleLarge = titleLarge.pixel(), titleMedium = titleMedium.pixel(), titleSmall = titleSmall.pixel(),
        bodyLarge = bodyLarge.pixel(), bodyMedium = bodyMedium.pixel(), bodySmall = bodySmall.pixel(),
        labelLarge = labelLarge.pixel(), labelMedium = labelMedium.pixel(), labelSmall = labelSmall.pixel(),
    )
}

/** Nearly square corners everywhere: the pixel-art look. */
private val PixelShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(3.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

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
