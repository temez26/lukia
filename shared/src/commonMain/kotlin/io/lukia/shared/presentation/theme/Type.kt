package io.lukia.shared.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.lukia.core.domain.AppPalette
import io.lukia.resources.Res
import io.lukia.resources.noto_serif_bold
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
 * Ledger's type: every heading and title in a serif, set bold, as a newspaper
 * sets its headlines. Body text and labels stay in the sans the rest of the app
 * uses - small serif text on a phone reads worse, not more bookish.
 *
 * Unlike [LukiaTypography] this one bundles its face (Noto Serif Bold - see
 * [ledgerHeadlineFamily]). The platform's own serif was tried first and is not
 * there to be had on every phone: a maker's font setting can map it to the
 * system sans, which left Ledger's headings indistinguishable from Crisp's.
 */
private fun ledgerTypography(headlines: FontFamily): Typography = LukiaTypography.run {
    fun TextStyle.headline() = copy(fontFamily = headlines, fontWeight = FontWeight.Bold)

    copy(
        displaySmall = displaySmall.headline(),
        headlineLarge = headlineLarge.headline(),
        headlineMedium = headlineMedium.headline(),
        headlineSmall = headlineSmall.headline(),
        titleLarge = titleLarge.headline(),
        titleMedium = titleMedium.headline(),
    )
}

/**
 * Only Ledger changes type; the other palettes share [LukiaTypography].
 * [ledgerHeadlines] is Ledger's heading face, loaded by the caller because a
 * bundled font can only be read inside a composition.
 */
internal fun typographyFor(palette: AppPalette, ledgerHeadlines: FontFamily): Typography = when (palette) {
    AppPalette.Crisp, AppPalette.Paper, AppPalette.Slate -> LukiaTypography
    AppPalette.Ledger -> ledgerTypography(ledgerHeadlines)
}

/** The face Ledger sets its headings in, bundled so it is the same on every device. */
@Composable
internal fun ledgerHeadlineFamily(): FontFamily = FontFamily(Font(Res.font.noto_serif_bold, FontWeight.Bold))
