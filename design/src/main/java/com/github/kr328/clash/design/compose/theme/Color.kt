package com.github.kr328.clash.design.compose.theme

import androidx.compose.ui.graphics.Color

/**
 * Compose color tokens. The XML palette (`res/values/colors.xml`) keeps the
 * same neutral greys for the few View-based dialogs left.
 */

// ---------------------------------------------------------------------------
// Neutral surfaces. The colour of the interface is the accent alone (Settings →
// Visual → Accent Color); everything else is grey, as on iOS.
// ---------------------------------------------------------------------------
internal val LightError = Color(0xFFB3261E)
internal val LightOnError = Color(0xFFFFFFFF)
internal val LightBackground = Color(0xFFFAFAFA)
internal val LightOnBackground = Color(0xFF1B1B1F)
internal val LightSurface = Color(0xFFFAFAFA)
internal val LightOnSurface = Color(0xFF1B1B1F)
internal val LightSurfaceVariant = Color(0xFFE4E4E7)
internal val LightOnSurfaceVariant = Color(0xFF47474C)
internal val LightOutline = Color(0xFF78787E)
internal val LightOutlineVariant = Color(0xFFC8C8CD)
internal val LightSurfaceContainer = Color(0xFFF0F0F3)
internal val LightSurfaceContainerHigh = Color(0xFFE8E8EB)
internal val LightSurfaceContainerHighest = Color(0xFFE0E0E3)
internal val LightSurfaceContainerLow = Color(0xFFF5F5F7)
internal val LightSurfaceDim = Color(0xFFDCDCDF)

internal val DarkError = Color(0xFFFFB4AB)
internal val DarkOnError = Color(0xFF690005)
internal val DarkBackground = Color(0xFF0F0F0F)
internal val DarkOnBackground = Color(0xFFE3E2E6)
internal val DarkSurface = Color(0xFF0F0F0F)
internal val DarkOnSurface = Color(0xFFE3E2E6)
internal val DarkSurfaceVariant = Color(0xFF444448)
internal val DarkOnSurfaceVariant = Color(0xFFC8C8CD)
internal val DarkOutline = Color(0xFF8E8E93)
internal val DarkOutlineVariant = Color(0xFF444448)
internal val DarkSurfaceContainer = Color(0xFF1E1E1E)
internal val DarkSurfaceContainerHigh = Color(0xFF2B2B2B)
internal val DarkSurfaceContainerHighest = Color(0xFF363636)
internal val DarkSurfaceContainerLow = Color(0xFF181818)
internal val DarkSurfaceBright = Color(0xFF333336)

// ---------------------------------------------------------------------------
// Extra (non-MaterialColorScheme) tokens used by the power button
// ---------------------------------------------------------------------------
internal val PowerOff = Color(0xFF3A3A3A)
internal val PowerOn = Color(0xFF4CAF50)
internal val ClashStopped = Color(0xFF808080)
