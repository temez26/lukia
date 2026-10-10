package io.lukia.features.reader.presentation.theme

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import io.lukia.features.reader.domain.ReaderSettings
import io.lukia.features.reader.domain.TextAlignment
import io.lukia.shared.presentation.theme.LukiaTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How the reader's alignment setting reaches the text, and what it deliberately
 * does not reach.
 */
@OptIn(ExperimentalTestApi::class)
class ReaderTextStyleTest {

    private fun styleFor(settings: ReaderSettings): TextStyle {
        var style: TextStyle? = null

        runComposeUiTest {
            setContent { LukiaTheme { style = readerTextStyle(settings) } }
        }

        return requireNotNull(style)
    }

    /**
     * Start rather than Left: a book set right-to-left is ragged on its left,
     * and naming a side would set it against its own writing.
     */
    @Test
    fun raggedFollowsTheDirectionTheTextIsWrittenIn() {
        val style = styleFor(ReaderSettings(alignment = TextAlignment.Ragged))

        assertEquals(TextAlign.Start, style.textAlign)
    }

    @Test
    fun justifiedStretchesTheLinesToBothEdges() {
        val style = styleFor(ReaderSettings(alignment = TextAlignment.Justified))

        assertEquals(TextAlign.Justify, style.textAlign)
    }

    /** A title stretched to both edges leaves gaps a sentence's worth wide. */
    @Test
    fun aHeadingIsNeverJustifiedWhateverTheProseIsSetTo() {
        val prose = styleFor(ReaderSettings(alignment = TextAlignment.Justified))

        assertEquals(TextAlign.Start, headingStyle(prose, level = 1).textAlign)
        assertEquals(TextAlign.Start, headingStyle(prose, level = 3).textAlign)
    }

    /**
     * Preformatted text is the one thing whose spacing is content: justifying it
     * would move the columns of a table and the indents of a program.
     */
    @Test
    fun preformattedTextIsNeverJustified() {
        val prose = styleFor(ReaderSettings(alignment = TextAlignment.Justified))

        assertEquals(TextAlign.Start, codeStyle(prose).textAlign)
    }

    /** The setting must not disturb what the other settings decided. */
    @Test
    fun alignmentLeavesTheRestOfTheStyleAlone() {
        val ragged = styleFor(ReaderSettings(alignment = TextAlignment.Ragged))
        val justified = styleFor(ReaderSettings(alignment = TextAlignment.Justified))

        assertEquals(ragged.fontSize, justified.fontSize)
        assertEquals(ragged.lineHeight, justified.lineHeight)
        assertEquals(ragged.fontFamily, justified.fontFamily)
    }

    /**
     * A book that centred its part titles keeps them centred - the heading rule
     * is that a title is never *justified*, not that it is always flush.
     */
    @Test
    fun aHeadingTheBookCentredStaysCentred() {
        val centred = styleFor(ReaderSettings()).copy(textAlign = TextAlign.Center)

        assertEquals(TextAlign.Center, headingStyle(centred, level = 1).textAlign)
    }
}
