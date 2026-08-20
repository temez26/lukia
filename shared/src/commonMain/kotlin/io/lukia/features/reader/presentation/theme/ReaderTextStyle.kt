package io.lukia.features.reader.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.ReaderTypeface
import io.lukia.features.reader.domain.TextAlignment

/**
 * The size body text is set at before the reader's own scale is applied.
 *
 * Chosen as a reading size rather than an interface size: the app's `bodyLarge`
 * is what a list row or a dialog is set in, and text meant to be read for an
 * hour wants to be a little larger than text meant to be glanced at.
 */
private val BaseTextSize: TextUnit = 17.sp

/**
 * How body text is set under the current settings.
 *
 * The one place the three typography settings turn into an actual style, so
 * every block in a book is measured against the same body - a heading is this
 * scaled up, a quotation is this indented, and nothing has a size of its own
 * that would stop following the reader's chosen one.
 *
 * Leading is a multiple of the size rather than a fixed distance, which is what
 * keeps "Relaxed" relaxed at every text size instead of tightening as the type
 * grows.
 */
@Composable
internal fun readerTextStyle(settings: ReaderSettings): TextStyle {
    val color = MaterialTheme.colorScheme.onSurface

    return remember(
        settings.textScale,
        settings.lineSpacing,
        settings.typeface,
        settings.alignment,
        color,
    ) {
        val size = BaseTextSize * settings.textScale

        TextStyle(
            color = color,
            fontSize = size,
            lineHeight = size * settings.lineSpacing.multiplier,
            fontFamily = when (settings.typeface) {
                ReaderTypeface.Serif -> FontFamily.Serif
                ReaderTypeface.SansSerif -> FontFamily.SansSerif
            },
            // Start rather than Left: a book set right-to-left is ragged on its
            // left, and naming a side here would set it against its own writing.
            textAlign = when (settings.alignment) {
                TextAlignment.Justified -> TextAlign.Justify
                TextAlignment.Ragged -> TextAlign.Start
            },
        )
    }
}
