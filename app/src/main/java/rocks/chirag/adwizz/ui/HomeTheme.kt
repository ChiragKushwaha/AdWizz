package rocks.chirag.adwizz.ui

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import rocks.chirag.adwizz.data.*

/**
 * The pixel sun (day) or moon (night) up in the sky is the theme switch: tap it and the other
 * theme spreads out from it. Drawn as part of the scenery rather than as a button.
 */
@Composable
internal fun ThemeQuickToggle() {
    val reveal = LocalThemeReveal.current
    val dark = isAppInDarkTheme()
    var center by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.size(56.dp)
            .testTag("themeToggle")
            .onGloballyPositioned { center = it.boundsInRoot().center }
            .clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
                role = androidx.compose.ui.semantics.Role.Button,
            ) { switchTheme(reveal, if (dark) ThemeMode.LIGHT else ThemeMode.DARK, center) }
            .semantics { contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode" },
        contentAlignment = Alignment.Center
    ) {
        // Sun sets / moon rises: the new body scales and fades in as the old one goes.
        AnimatedContent(
            targetState = dark, label = "celestial",
            transitionSpec = {
                (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.4f))
                    .togetherWith(fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.4f))
            }
        ) { isDark -> PixelCelestial(moon = isDark, modifier = Modifier.size(48.dp)) }
    }
}

/** Saves and applies a theme. Called by [ThemeRevealHost] once it has snapshotted the old theme. */
fun applyTheme(context: Context, mode: ThemeMode) {
    Settings(context).themeMode = mode.key
    Telemetry.event(context, "theme_changed", "mode" to mode.key)
    ThemeState.mode.value = mode
}

internal fun switchTheme(reveal: ThemeRevealController?, mode: ThemeMode, origin: Offset) {
    if (reveal != null) reveal.switchTo(mode, origin) else ThemeState.mode.value = mode
}
