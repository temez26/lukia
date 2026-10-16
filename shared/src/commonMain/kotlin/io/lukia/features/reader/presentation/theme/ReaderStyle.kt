package io.lukia.features.reader.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.luminance
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.shared.presentation.theme.LocalThemeChoice
import io.lukia.shared.presentation.theme.LukiaDarkColorScheme
import io.lukia.shared.presentation.theme.LukiaLightColorScheme
import io.lukia.shared.presentation.theme.SystemBarIcons

/**
 * How a page is painted under the current reader theme.
 *
 * [pageFilter] is applied when the page is drawn rather than when it is
 * rendered. That is the whole reason changing theme is instant even in a long
 * book: not one page is re-rendered, and the cache stays warm - the same
 * bitmaps are simply drawn through a different filter.
 */
data class ReaderPageStyle(
    /** The sheet colour, seen while a page is still rendering. */
    val pageColor: Color,
    val pageFilter: ColorFilter?,
)

private val SepiaPage = Color(0xFFF4ECD8)
private val SepiaBackground = Color(0xFFE8DCC3)
private val SepiaSurface = Color(0xFFFBF5E6)
private val SepiaOnSurface = Color(0xFF4A3F30)
private val SepiaOnSurfaceVariant = Color(0xFF7A6B55)
private val SepiaOutline = Color(0xFFCBB894)
private val SepiaAccent = Color(0xFF9A6434)

/** Near-black once inverted, so a dark page is not a black hole beside the chrome. */
private val DarkPage = Color(0xFF121212)

/**
 * The page's colours for [theme]: the ink, and the fills of quotations, code and
 * passage marks. Fixed whatever palette is chosen, so a page reads the same in
 * every theme - see [ReaderPageTheming].
 */
@Composable
internal fun readerPageColorScheme(theme: ReaderTheme): ColorScheme = remember(theme) {
    when (theme) {
        ReaderTheme.Light -> LukiaLightColorScheme
        ReaderTheme.Dark -> LukiaDarkColorScheme
        ReaderTheme.Sepia -> LukiaLightColorScheme.copy(
            primary = SepiaAccent,
            onPrimary = Color.White,
            secondary = SepiaAccent,
            background = SepiaBackground,
            onBackground = SepiaOnSurface,
            surface = SepiaSurface,
            onSurface = SepiaOnSurface,
            surfaceVariant = SepiaBackground,
            onSurfaceVariant = SepiaOnSurfaceVariant,
            surfaceContainer = SepiaSurface,
            surfaceContainerHigh = SepiaSurface,
            outline = SepiaOutline,
            outlineVariant = SepiaOutline.copy(alpha = 0.5f),
        )
    }
}

@Composable
internal fun readerPageStyle(theme: ReaderTheme): ReaderPageStyle = remember(theme) {
    when (theme) {
        ReaderTheme.Light -> ReaderPageStyle(pageColor = Color.White, pageFilter = null)

        ReaderTheme.Sepia -> ReaderPageStyle(
            pageColor = SepiaPage,
            // Scales each channel so white paper lands on the sepia page colour
            // while ink stays ink. A true sepia matrix would also drain the
            // colour out of the document's own figures.
            pageFilter = ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.957f, 0f, 0f, 0f, 0f,
                        0f, 0.925f, 0f, 0f, 0f,
                        0f, 0f, 0.847f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            ),
        )

        ReaderTheme.Dark -> ReaderPageStyle(
            pageColor = DarkPage,
            // Inverted and pulled in at both ends: paper lands just above black
            // and ink just below white, which is far easier to read for long
            // stretches than a hard invert's pure black on pure white.
            pageFilter = ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        -0.85f, 0f, 0f, 0f, 222f,
                        0f, -0.85f, 0f, 0f, 222f,
                        0f, 0f, -0.85f, 0f, 222f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            ),
        )
    }
}

/**
 * The chrome's colours for [theme]: the chosen palette and accent at the
 * reader's lightness in Light and Dark, so the toolbar and panels look like the
 * rest of the app. Sepia keeps colours of its own, which are part of what
 * choosing it means.
 */
@Composable
internal fun readerChromeColorScheme(theme: ReaderTheme): ColorScheme {
    val choice = LocalThemeChoice.current
    val page = readerPageColorScheme(theme)

    return remember(theme, choice, page) {
        when (theme) {
            ReaderTheme.Light -> choice.colorScheme(darkTheme = false)
            ReaderTheme.Dark -> choice.colorScheme(darkTheme = true)
            ReaderTheme.Sepia -> page
        }
    }
}

/**
 * Wraps the reader in its own lightness. Scoped to the reader alone: the library
 * and detail screens keep following the system's light or dark setting. Every
 * control already in the design system - the glass surfaces, the type, the
 * buttons - follows it without being told about it.
 */
@Composable
internal fun ReaderTheming(
    theme: ReaderTheme,
    content: @Composable () -> Unit,
) {
    val colors = readerChromeColorScheme(theme)

    SystemBarIcons(dark = colors.background.luminance() > LIGHT_PAGE_LUMINANCE)

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}

/** Above this a page is pale enough that only dark icons show up against it. */
private const val LIGHT_PAGE_LUMINANCE = 0.5f

/**
 * Sets the page itself in [readerPageColorScheme], inside chrome that wears the
 * chosen palette. The page is the book, and a palette is a choice about the app
 * around it.
 */
@Composable
internal fun ReaderPageTheming(
    theme: ReaderTheme,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = readerPageColorScheme(theme),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content,
    )
}
