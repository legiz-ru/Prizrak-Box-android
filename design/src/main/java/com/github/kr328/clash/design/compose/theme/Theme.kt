package com.github.kr328.clash.design.compose.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * The scheme around an accent: the accent is primary (and what Material 3
 * derives from it — selections, the navigation pill, filled buttons); every
 * other role is a neutral grey, so nothing else colours the interface.
 */
private fun scheme(dark: Boolean, accent: Color): ColorScheme {
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    return if (dark) {
        val container = lerp(DarkSurfaceContainerHigh, accent, 0.32f)
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = container,
            onPrimaryContainer = DarkOnSurface,
            inversePrimary = accent,
            secondary = DarkOnSurfaceVariant,
            onSecondary = DarkSurface,
            secondaryContainer = container,
            onSecondaryContainer = DarkOnSurface,
            tertiary = DarkOnSurfaceVariant,
            onTertiary = DarkSurface,
            tertiaryContainer = DarkSurfaceContainerHighest,
            onTertiaryContainer = DarkOnSurface,
            error = DarkError,
            onError = DarkOnError,
            background = DarkBackground,
            onBackground = DarkOnBackground,
            surface = DarkSurface,
            onSurface = DarkOnSurface,
            surfaceVariant = DarkSurfaceVariant,
            onSurfaceVariant = DarkOnSurfaceVariant,
            // No tint of the accent on raised surfaces.
            surfaceTint = Color.Transparent,
            outline = DarkOutline,
            outlineVariant = DarkOutlineVariant,
            surfaceContainer = DarkSurfaceContainer,
            surfaceContainerHigh = DarkSurfaceContainerHigh,
            surfaceContainerHighest = DarkSurfaceContainerHighest,
            surfaceContainerLow = DarkSurfaceContainerLow,
            surfaceContainerLowest = DarkBackground,
            surfaceBright = DarkSurfaceBright,
            surfaceDim = DarkBackground,
        )
    } else {
        val container = lerp(LightSurfaceContainer, accent, 0.18f)
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = container,
            onPrimaryContainer = LightOnSurface,
            inversePrimary = accent,
            secondary = LightOnSurfaceVariant,
            onSecondary = LightSurface,
            secondaryContainer = container,
            onSecondaryContainer = LightOnSurface,
            tertiary = LightOnSurfaceVariant,
            onTertiary = LightSurface,
            tertiaryContainer = LightSurfaceContainerHighest,
            onTertiaryContainer = LightOnSurface,
            error = LightError,
            onError = LightOnError,
            background = LightBackground,
            onBackground = LightOnBackground,
            surface = LightSurface,
            onSurface = LightOnSurface,
            surfaceVariant = LightSurfaceVariant,
            onSurfaceVariant = LightOnSurfaceVariant,
            surfaceTint = Color.Transparent,
            outline = LightOutline,
            outlineVariant = LightOutlineVariant,
            surfaceContainer = LightSurfaceContainer,
            surfaceContainerHigh = LightSurfaceContainerHigh,
            surfaceContainerHighest = LightSurfaceContainerHighest,
            surfaceContainerLow = LightSurfaceContainerLow,
            surfaceContainerLowest = Color.White,
            surfaceBright = Color.White,
            surfaceDim = LightSurfaceDim,
        )
    }
}

/** The app's green: the accent while nothing else is chosen or known, and Settings' switches. */
val AppGreen = Color(0xFF000000 or AccentColours.GREEN.toLong())

/**
 * Palette tokens that are not part of [ColorScheme] but are needed by custom
 * components such as the ghost power button. Exposed through a CompositionLocal
 * so screens read them the same way as [MaterialTheme.colorScheme].
 */
@Immutable
data class ClashExtraColors(
    val powerOff: Color = PowerOff,
    val powerOn: Color = PowerOn,
    val clashStopped: Color = ClashStopped,
    /** Switches and checkmarks of Settings: on/off, not a profile — green whatever the accent, as on iOS. */
    val settingsGreen: Color = AppGreen,
)

val LocalClashExtraColors = staticCompositionLocalOf { ClashExtraColors() }

object ClashTheme {
    val extraColors: ClashExtraColors
        @Composable
        get() = LocalClashExtraColors.current
}

/**
 * Root theme for all Compose surfaces in the app.
 *
 * @param accent the interface's accent for this light or dark look.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ClashTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color = AppGreen,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalClashExtraColors provides ClashExtraColors(),
    ) {
        // Material 3 Expressive: its springy motion and its shapes.
        MaterialExpressiveTheme(
            colorScheme = scheme(darkTheme, accent),
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
