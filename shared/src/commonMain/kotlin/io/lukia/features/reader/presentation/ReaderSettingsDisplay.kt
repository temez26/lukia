package io.lukia.features.reader.presentation

import androidx.compose.runtime.Composable
import io.lukia.features.reader.domain.FigureSize
import io.lukia.features.reader.domain.LineSpacing
import io.lukia.features.reader.domain.ReaderTheme
import io.lukia.features.reader.domain.ReaderTypeface
import io.lukia.features.reader.domain.ReadingWidth
import io.lukia.features.reader.domain.TextAlignment
import io.lukia.resources.Res
import io.lukia.resources.reader_alignment_justified
import io.lukia.resources.reader_alignment_ragged
import io.lukia.resources.reader_figure_fill
import io.lukia.resources.reader_figure_large
import io.lukia.resources.reader_figure_normal
import io.lukia.resources.reader_figure_small
import io.lukia.resources.reader_spacing_normal
import io.lukia.resources.reader_spacing_relaxed
import io.lukia.resources.reader_spacing_tight
import io.lukia.resources.reader_theme_dark
import io.lukia.resources.reader_theme_light
import io.lukia.resources.reader_theme_sepia
import io.lukia.resources.reader_typeface_sans
import io.lukia.resources.reader_typeface_serif
import io.lukia.resources.reader_width_percent
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * What every reading setting is called.
 *
 * One file for all six, and shared by the two screens that offer them - the
 * reader's own panel and the settings screen. They used to carry their own
 * copies of these labels, mostly by printing the enum constant's name, so a
 * choice could read one way over the book and another way in Settings.
 */
@Composable
fun ReaderTheme.displayName(): String = when (this) {
    ReaderTheme.Light -> stringResource(Res.string.reader_theme_light)
    ReaderTheme.Sepia -> stringResource(Res.string.reader_theme_sepia)
    ReaderTheme.Dark -> stringResource(Res.string.reader_theme_dark)
}

@Composable
fun ReaderTypeface.displayName(): String = when (this) {
    ReaderTypeface.Serif -> stringResource(Res.string.reader_typeface_serif)
    ReaderTypeface.SansSerif -> stringResource(Res.string.reader_typeface_sans)
}

@Composable
fun TextAlignment.displayName(): String = when (this) {
    TextAlignment.Ragged -> stringResource(Res.string.reader_alignment_ragged)
    TextAlignment.Justified -> stringResource(Res.string.reader_alignment_justified)
}

@Composable
fun LineSpacing.displayName(): String = when (this) {
    LineSpacing.Tight -> stringResource(Res.string.reader_spacing_tight)
    LineSpacing.Normal -> stringResource(Res.string.reader_spacing_normal)
    LineSpacing.Relaxed -> stringResource(Res.string.reader_spacing_relaxed)
}

@Composable
fun FigureSize.displayName(): String = when (this) {
    FigureSize.Small -> stringResource(Res.string.reader_figure_small)
    FigureSize.Normal -> stringResource(Res.string.reader_figure_normal)
    FigureSize.Large -> stringResource(Res.string.reader_figure_large)
    FigureSize.Fill -> stringResource(Res.string.reader_figure_fill)
}

/**
 * A width as the share of the window it takes.
 *
 * A format rather than a "%" glued to a number: several languages put a space
 * before the sign, and one of them writes the sign on the other side.
 */
@Composable
fun ReadingWidth.displayName(): String =
    stringResource(Res.string.reader_width_percent, (fraction * 100).roundToInt())
