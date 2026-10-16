package io.lukia.shared.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.lukia.core.domain.AppPalette
import io.lukia.resources.Res
import io.lukia.resources.noto_serif_bold
import io.lukia.resources.noto_serif_regular
import org.jetbrains.compose.resources.Font

/**
 * Type scale mirroring Apple's SF text styles (Large Title / Title / Headline /
 * Body / Footnote). No font is bundled: each platform's default already resolves
 * to its system face - SF on Apple platforms, Roboto on Android - which is what
 * makes the app feel native. Only the sizes, weights and tracking are tuned here.
 *
 * Large sizes get negative tracking, matching how Apple tightens display text.
 */
internal val LukiaTypography = Typography(
    // Large Title - iOS 34pt bold
    displaySmall = TextStyle(
        fontSize = 34.sp,
        lineHeight = 41.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.5).sp,
    ),
    headlineLarge = TextStyle(
        fontSize = 28.sp,
        lineHeight = 34.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.4).sp,
    ),
    headlineMedium = TextStyle(
        fontSize = 24.sp,
        lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    /**
     * Title 1 - iOS 20pt.
     *
     * Set here rather than left to Material, which was what the statistics
     * tiles, the home rows and a looked-up word were all wearing: Material's own
     * `headlineSmall` is 24sp Normal with no tracking, so it landed at the same
     * size as [Typography.headlineMedium] above, in a different weight, with the
     * negative tracking the rest of this scale is built on missing.
     */
    headlineSmall = TextStyle(
        fontSize = 20.sp,
        lineHeight = 25.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    // Title 2 - iOS 22pt
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    // Headline - iOS 17pt semibold
    titleMedium = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    titleSmall = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp,
    ),
    // Body - iOS 17pt regular
    bodyLarge = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.1).sp,
    ),
    // Subheadline - iOS 15pt
    bodyMedium = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    // Footnote - iOS 13pt
    bodySmall = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Normal,
    ),
    // Buttons pick up labelLarge
    labelLarge = TextStyle(
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.2).sp,
    ),
    labelMedium = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    // Caption - iOS 12pt, slightly opened up for legibility at small sizes
    labelSmall = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
    ),
)

/**
 * Paper's type: headings in the serif at its regular weight, the way a novel
 * sets its chapter titles - present, not loud. Body text and labels stay sans.
 */
private fun paperTypography(serif: FontFamily): Typography = LukiaTypography.withHeadlines {
    copy(fontFamily = serif, fontWeight = FontWeight.Normal, letterSpacing = 0.sp)
}

/**
 * Slate's type: the system sans, heavier and tighter in its headings, as a
 * dashboard sets them. Nothing bundled - its character is weight, not face.
 */
private val SlateTypography: Typography = LukiaTypography.withHeadlines {
    copy(
        fontWeight = if (fontWeight == FontWeight.Bold) FontWeight.ExtraBold else FontWeight.Bold,
        letterSpacing = (letterSpacing.value - SlateTightening).sp,
    )
}

/** How much tighter Slate sets its headings than [LukiaTypography] does, in sp. */
private const val SlateTightening = 0.3f

/**
 * Ledger's type: every heading in the serif, set bold, as a newspaper sets its
 * headlines.
 */
private fun ledgerTypography(serif: FontFamily): Typography = LukiaTypography.withHeadlines {
    copy(fontFamily = serif, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
}

/**
 * The scale with [restyle] applied to its headings and titles, and its body text
 * and labels left alone - small type in anything but the system sans reads worse
 * on a phone, not more characterful.
 */
private fun Typography.withHeadlines(restyle: TextStyle.() -> TextStyle): Typography = copy(
    displaySmall = displaySmall.restyle(),
    headlineLarge = headlineLarge.restyle(),
    headlineMedium = headlineMedium.restyle(),
    headlineSmall = headlineSmall.restyle(),
    titleLarge = titleLarge.restyle(),
    titleMedium = titleMedium.restyle(),
)

/**
 * Each palette's type. [serif] is the bundled serif, loaded by the caller because
 * a bundled font can only be read inside a composition.
 */
internal fun typographyFor(palette: AppPalette, serif: FontFamily): Typography = when (palette) {
    AppPalette.Crisp -> LukiaTypography
    AppPalette.Paper -> paperTypography(serif)
    AppPalette.Slate -> SlateTypography
    AppPalette.Ledger -> ledgerTypography(serif)
}

/**
 * [typographyFor] for the palette in use, reading the bundled serif only for the
 * palettes that set headings in it.
 *
 * Reading the font is not free: on desktop it is read from the jar, blocking, in
 * the first composition, and on iOS it arrives later and redraws every line of
 * text in the app when it does. Asked for unconditionally, Crisp and Slate paid
 * both for a face they never draw.
 */
@Composable
internal fun rememberTypography(palette: AppPalette): Typography {
    val serif = if (palette.setsHeadingsInSerif()) bundledSerif() else FontFamily.Default

    return remember(palette, serif) { typographyFor(palette, serif) }
}

/** Whether [typographyFor] uses the serif it is given for this palette. */
internal fun AppPalette.setsHeadingsInSerif(): Boolean = when (this) {
    AppPalette.Crisp, AppPalette.Slate -> false
    AppPalette.Paper, AppPalette.Ledger -> true
}

/**
 * Noto Serif, which Paper and Ledger set their headings in.
 *
 * Bundled rather than the platform's own serif, which is not there to be had on
 * every phone: a maker's font setting can map it to the system sans, which left
 * Ledger's headings looking like Crisp's.
 */
@Composable
private fun bundledSerif(): FontFamily = FontFamily(
    Font(Res.font.noto_serif_regular, FontWeight.Normal),
    Font(Res.font.noto_serif_bold, FontWeight.Bold),
)
