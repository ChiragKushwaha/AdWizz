package rocks.chirag.adshield.ui

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

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

internal val LightColors = lightColorScheme(
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

internal val DarkColors = darkColorScheme(
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
