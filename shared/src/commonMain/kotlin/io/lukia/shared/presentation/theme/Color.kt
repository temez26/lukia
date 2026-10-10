package io.lukia.shared.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import io.lukia.core.domain.AppAccent
import io.lukia.core.domain.AppPalette

/**
 * One palette's surfaces, at one lightness.
 *
 * Named for what each colour is for rather than for what it looks like, so the
 * three palettes below can be read side by side and compared row for row.
 */
private class Surfaces(
    /** Behind everything, and what cards are laid on. */
    val background: Color,
    /** A card, a menu, a bar - whatever sits on [background]. */
    val elevated: Color,
    /** A recessed fill on a card: a field, a track, a chip. */
    val secondary: Color,
    val label: Color,
    /**
     * Everything a screen says that is not its subject: an author under a title,
     * the value on a settings row, a book's format and size, the words of a
     * quotation.
     *
     * Held to 4.5:1 against all three surfaces above rather than to a shade
     * that looked right on white: Apple's own secondaryLabel is 3.4:1 on white
     * and 2.8:1 on a recessed fill, and this app sets whole quoted paragraphs in
     * it - see `QuoteBlock`.
     */
    val secondaryLabel: Color,
    val separator: Color,
)

/**
 * Apple's system colours, which is what the app wore before it wore anything
 * else: white cards on grey by day, true black with progressively lighter
 * surfaces at night. Crisp is the reader's word for it.
 */
private val CrispLight = Surfaces(
    background = Color(0xFFF2F2F7),
    elevated = Color(0xFFFFFFFF),
    secondary = Color(0xFFE9E9EF),
    label = Color(0xFF1C1C1E),
    secondaryLabel = Color(0xFF69696C),
    separator = Color(0xFFC6C6C8),
)

private val CrispDark = Surfaces(
    background = Color(0xFF000000),
    elevated = Color(0xFF1C1C1E),
    secondary = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0xFF98989D),
    separator = Color(0xFF38383A),
)

/**
 * Paper and ink: warm off-white surfaces with a warm grey cast to the text, and
 * a night side that is brown-black rather than blue-black. The difference
 * between a lamp and a screen, which is the whole of why somebody picks it.
 *
 * Modelled on Readest, which is where the warmth comes from.
 */
private val PaperLight = Surfaces(
    background = Color(0xFFF3EEE4),
    elevated = Color(0xFFFDFAF3),
    secondary = Color(0xFFE9E1D3),
    label = Color(0xFF26221D),
    secondaryLabel = Color(0xFF6C6357),
    separator = Color(0xFFDBD1BF),
)

private val PaperDark = Surfaces(
    background = Color(0xFF12100E),
    elevated = Color(0xFF1E1B17),
    secondary = Color(0xFF2A2620),
    label = Color(0xFFF2ECE1),
    secondaryLabel = Color(0xFFA79C8C),
    separator = Color(0xFF37312A),
)

/**
 * Cool neutral greys, a true white card, and one confident accent doing all the
 * work. Reads as a tool rather than as a book.
 *
 * Modelled on Readwise Reader, down to the amber it highlights with.
 */
private val SlateLight = Surfaces(
    background = Color(0xFFF4F4F5),
    elevated = Color(0xFFFFFFFF),
    secondary = Color(0xFFE4E4E7),
    label = Color(0xFF18181B),
    secondaryLabel = Color(0xFF65656E),
    separator = Color(0xFFD4D4D8),
)

private val SlateDark = Surfaces(
    background = Color(0xFF09090B),
    elevated = Color(0xFF18181B),
    secondary = Color(0xFF27272A),
    label = Color(0xFFFAFAFA),
    secondaryLabel = Color(0xFFA1A1AA),
    separator = Color(0xFF3F3F46),
)

/** The paper palette's blue, a shade warmer and deeper than Apple's. */
private val PaperAccent = Color(0xFF0964BF)
private val PaperAccentDark = Color(0xFF6FB2FF)

/**
 * Deeper than Apple's systemRed, for the reason the accents are - see [Accents].
 *
 * This is not only a fill. It is the colour a failed translation is printed in,
 * and the colour of the label on a destructive button; Apple's own red carries
 * 2.7:1 as text on a light surface and 3.6:1 under a white label, both of which
 * are for a badge rather than for a sentence.
 */
private val ErrorRed = Color(0xFFBE1E15)
private val ErrorRedDark = Color(0xFFFF6961)

/**
 * The colours the app is painted in: one palette's surfaces, at one lightness,
 * highlighted with whichever accent the reader chose.
 *
 * Assembled here rather than written out six times over, because what differs
 * between the palettes is those six surface colours and nothing at all about how
 * they are used.
 */
internal fun colorSchemeFor(
    palette: AppPalette,
    darkTheme: Boolean,
    accent: AppAccent,
): ColorScheme {
    val highlight = accentColor(accent, darkTheme) ?: themedAccentColor(palette, darkTheme)

    // The same palette seen from the other side, which is where every "inverse"
    // role is answered from - see [Surfaces.scheme].
    val opposite = surfacesFor(palette, !darkTheme)
    val oppositeHighlight =
        accentColor(accent, !darkTheme) ?: themedAccentColor(palette, !darkTheme)

    return surfacesFor(palette, darkTheme)
        .scheme(darkTheme, opposite)
        .withAccent(highlight, oppositeHighlight, darkTheme)
}

/**
 * The app's own palette, at its own accent.
 *
 * For the reader, whose Light, Dark and Sepia are a property of the page being
 * read rather than of the app around it: a book is on white, black or paper
 * because its reader said so, and no choice made in Settings should repaint the
 * sheet somebody is reading off.
 */
internal val LukiaLightColorScheme: ColorScheme =
    colorSchemeFor(AppPalette.Crisp, darkTheme = false, accent = AppAccent.Themed)

internal val LukiaDarkColorScheme: ColorScheme =
    colorSchemeFor(AppPalette.Crisp, darkTheme = true, accent = AppAccent.Themed)

/**
 * What [AppAccent.Themed] resolves to: the highlight a palette brings with it.
 *
 * Kept beside the surfaces rather than among them, because an accent is the one
 * colour of a palette a reader is allowed to overrule.
 */
internal fun themedAccentColor(palette: AppPalette, darkTheme: Boolean): Color = when (palette) {
    AppPalette.Crisp -> if (darkTheme) Accents.BlueDark else Accents.Blue
    AppPalette.Paper -> if (darkTheme) PaperAccentDark else PaperAccent
    AppPalette.Slate -> if (darkTheme) Accents.AmberDark else Accents.Amber
}

private fun surfacesFor(palette: AppPalette, darkTheme: Boolean): Surfaces = when (palette) {
    AppPalette.Crisp -> if (darkTheme) CrispDark else CrispLight
    AppPalette.Paper -> if (darkTheme) PaperDark else PaperLight
    AppPalette.Slate -> if (darkTheme) SlateDark else SlateLight
}

/**
 * [opposite] is the same palette at the other lightness, and is what fills
 * Material's `inverse` roles.
 *
 * Those roles are not optional: `Snackbar` and `PlainTooltip` are painted
 * entirely out of them, and both wore Material's baseline neutrals - a cold
 * lilac-grey - over the Paper palette's warm surfaces until the app set them.
 * Nothing here inverts a channel: the light theme's inverse surface is literally
 * the dark theme's card, already held to the same contrast as everything else.
 */
private fun Surfaces.scheme(darkTheme: Boolean, opposite: Surfaces): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()

    return base.copy(
        background = background,
        onBackground = label,
        surface = elevated,
        onSurface = label,
        surfaceVariant = secondary,
        onSurfaceVariant = secondaryLabel,
        surfaceContainer = elevated,
        // The one surface that differs by lightness rather than by palette: a
        // light theme raises by staying white, a dark one by growing paler.
        surfaceContainerHigh = if (darkTheme) secondary else elevated,
        // What an unchecked `Switch` draws its track in, and the only Material
        // component in the app that reaches past `surfaceVariant` for a
        // recessed fill. Same colour, so a switch in a settings row sits on the
        // palette rather than beside it.
        surfaceContainerHighest = secondary,
        inverseSurface = opposite.elevated,
        inverseOnSurface = opposite.label,
        outline = separator,
        outlineVariant = separator.copy(alpha = if (darkTheme) 0.6f else 0.5f),
        error = if (darkTheme) ErrorRedDark else ErrorRed,
        onError = (if (darkTheme) ErrorRedDark else ErrorRed).readableOn(),
    )
}

/**
 * The same scheme, highlighted. Everything Material reaches for when it means
 * "this one" points at a single colour, so an accent chosen in Settings reaches
 * the progress bar, the chosen row and the button that does the thing without
 * any of them knowing that a setting exists.
 */
private fun ColorScheme.withAccent(
    accent: Color,
    onInverse: Color,
    darkTheme: Boolean,
): ColorScheme = copy(
    primary = accent,
    onPrimary = accent.readableOn(),
    primaryContainer = accent.copy(alpha = if (darkTheme) 0.24f else 0.12f),
    onPrimaryContainer = accent,
    secondary = accent,
    onSecondary = accent.readableOn(),
    // The accent as it is drawn on `inverseSurface` - a snackbar's action - which
    // is the other theme's accent for the same reason that surface is the other
    // theme's card: it is the one already measured against that ground.
    inversePrimary = onInverse,
)

/**
 * Black or white, whichever can be read on this colour.
 *
 * Every accent used to be labelled in white: right for the seven deep ones a
 * light theme picks, wrong for the seven pale ones a dark theme does - white on
 * the dark side's green measured 2.0:1, its amber 2.1:1, and a filled button is
 * the one place a label has nowhere else to go.
 *
 * Decided from the colour rather than from which theme is on: a palette's own
 * accent, a chosen one and the error colour all arrive here, and only the colour
 * knows how light it is.
 */
private fun Color.readableOn(): Color =
    if (luminance() > OnColorThreshold) Color.Black else Color.White

/**
 * Where the swap happens. The app's own colours sit either side of it with room
 * to spare - the deepest of the pale accents is at 0.29 and the palest of the
 * deep ones at 0.13 - so this is a boundary between two groups rather than a
 * line any of them is near.
 */
private const val OnColorThreshold = 0.2f
