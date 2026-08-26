package io.lukia.shared.presentation.theme

import androidx.compose.ui.graphics.Color
import io.lukia.core.domain.AppAccent
import io.lukia.core.domain.AppPalette

/**
 * The accents a reader may choose from.
 *
 * Two of each: a deeper one for light surfaces and a lighter one for dark, since
 * no single hue carries text-weight contrast against both. Apple's system
 * colours where Apple has one, pulled a shade darker on the light side where the
 * system colour was chosen for a bar rather than for a word.
 */
internal object Accents {
    val Blue = Color(0xFF007AFF)
    val BlueDark = Color(0xFF0A84FF)

    val Violet = Color(0xFF6D46D9)
    val VioletDark = Color(0xFFA98BFF)

    val Green = Color(0xFF1E8E43)
    val GreenDark = Color(0xFF30D158)

    val Amber = Color(0xFFB4600A)
    val AmberDark = Color(0xFFF0A32A)

    val Rose = Color(0xFFC81E4E)
    val RoseDark = Color(0xFFFF6B8A)

    val Graphite = Color(0xFF4B5563)
    val GraphiteDark = Color(0xFF9CA3AF)
}

/**
 * The colour behind a chosen accent, or null for [AppAccent.Themed] - which is
 * not a colour but a deferral to whichever palette is on, and so is answered
 * where the palettes are.
 */
internal fun accentColor(accent: AppAccent, darkTheme: Boolean): Color? = when (accent) {
    AppAccent.Themed -> null
    AppAccent.Blue -> if (darkTheme) Accents.BlueDark else Accents.Blue
    AppAccent.Violet -> if (darkTheme) Accents.VioletDark else Accents.Violet
    AppAccent.Green -> if (darkTheme) Accents.GreenDark else Accents.Green
    AppAccent.Amber -> if (darkTheme) Accents.AmberDark else Accents.Amber
    AppAccent.Rose -> if (darkTheme) Accents.RoseDark else Accents.Rose
    AppAccent.Graphite -> if (darkTheme) Accents.GraphiteDark else Accents.Graphite
}

/**
 * What a swatch in Settings is painted with. [AppAccent.Themed] has no colour of
 * its own, so it wears whichever one [palette] is already highlighting with -
 * which is exactly what choosing it means.
 */
internal fun accentSwatchColor(
    accent: AppAccent,
    darkTheme: Boolean,
    palette: AppPalette,
): Color = accentColor(accent, darkTheme) ?: themedAccentColor(palette, darkTheme)
