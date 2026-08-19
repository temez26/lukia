package io.lukia.shared.presentation.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Palette derived from Apple's system colors so the app feels native on Apple
 * platforms while staying neutral enough for Android and desktop.
 */
private object AppleColors {
    val Blue = Color(0xFF007AFF)
    val BlueDark = Color(0xFF0A84FF)

    val Red = Color(0xFFFF3B30)
    val RedDark = Color(0xFFFF453A)

    // Light: grouped background is grey, cards sit on top of it in white.
    val GroupedBackground = Color(0xFFF2F2F7)
    val ElevatedSurface = Color(0xFFFFFFFF)
    val SecondarySurface = Color(0xFFE9E9EF)
    val Label = Color(0xFF1C1C1E)
    val SecondaryLabel = Color(0xFF8A8A8E)
    val Separator = Color(0xFFC6C6C8)

    // Dark: true black background with progressively lighter elevated surfaces.
    val GroupedBackgroundDark = Color(0xFF000000)
    val ElevatedSurfaceDark = Color(0xFF1C1C1E)
    val SecondarySurfaceDark = Color(0xFF2C2C2E)
    val LabelDark = Color(0xFFFFFFFF)
    val SecondaryLabelDark = Color(0xFF98989D)
    val SeparatorDark = Color(0xFF38383A)
}

internal val LukiaLightColorScheme = lightColorScheme(
    primary = AppleColors.Blue,
    onPrimary = Color.White,
    primaryContainer = AppleColors.Blue.copy(alpha = 0.12f),
    onPrimaryContainer = AppleColors.Blue,
    secondary = AppleColors.Blue,
    onSecondary = Color.White,
    background = AppleColors.GroupedBackground,
    onBackground = AppleColors.Label,
    surface = AppleColors.ElevatedSurface,
    onSurface = AppleColors.Label,
    surfaceVariant = AppleColors.SecondarySurface,
    onSurfaceVariant = AppleColors.SecondaryLabel,
    surfaceContainer = AppleColors.ElevatedSurface,
    surfaceContainerHigh = AppleColors.ElevatedSurface,
    outline = AppleColors.Separator,
    outlineVariant = AppleColors.Separator.copy(alpha = 0.5f),
    error = AppleColors.Red,
    onError = Color.White,
)

internal val LukiaDarkColorScheme = darkColorScheme(
    primary = AppleColors.BlueDark,
    onPrimary = Color.White,
    primaryContainer = AppleColors.BlueDark.copy(alpha = 0.24f),
    onPrimaryContainer = AppleColors.BlueDark,
    secondary = AppleColors.BlueDark,
    onSecondary = Color.White,
    background = AppleColors.GroupedBackgroundDark,
    onBackground = AppleColors.LabelDark,
    surface = AppleColors.ElevatedSurfaceDark,
    onSurface = AppleColors.LabelDark,
    surfaceVariant = AppleColors.SecondarySurfaceDark,
    onSurfaceVariant = AppleColors.SecondaryLabelDark,
    surfaceContainer = AppleColors.ElevatedSurfaceDark,
    surfaceContainerHigh = AppleColors.SecondarySurfaceDark,
    outline = AppleColors.SeparatorDark,
    outlineVariant = AppleColors.SeparatorDark.copy(alpha = 0.6f),
    error = AppleColors.RedDark,
    onError = Color.White,
)
