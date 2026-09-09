package io.lukia.features.reader.infrastructure

import io.lukia.features.reader.domain.page.PageGlyph
import io.lukia.features.reader.domain.page.TextQuad
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import platform.Foundation.NSAttributedString
import platform.Foundation.NSRange
import platform.PDFKit.PDFPage
import platform.PDFKit.kPDFDisplayBoxCropBox
import platform.UIKit.NSFontAttributeName
import platform.UIKit.UIFont

/**
 * Every positioned character of a page, the right way up and in the shape the
 * rest of the reader measures text in.
 *
 * PDFKit measures upwards from the bottom left of the page's box, and every
 * screen the app draws on measures downwards from the top left - so each box is
 * flipped here, once, rather than at each place that draws one. The box's own
 * origin is taken off as well: a page cropped to a margin does not start at
 * zero, and text placed against an uncropped origin would sit that margin away
 * from the words it belongs to.
 *
 * A quarter-turned page returns nothing. PDFKit reports its characters in the
 * page's unrotated space while rendering it rotated, so the two do not line up -
 * and an overlay drawn ninety degrees away from its sentence is worse than no
 * overlay at all.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun PDFPage.pageGlyphs(): List<PageGlyph>? {
    if (rotation % HALF_TURN != 0L) return null

    val text = string ?: return null
    val box = boundsForBox(kPDFDisplayBoxCropBox)
    val originX = box.useContents { origin.x }
    val topEdge = box.useContents { origin.y + size.height }

    val fonts = TypeSizes(attributedString)
    val glyphs = mutableListOf<PageGlyph>()
    var line = mutableListOf<InkGlyph>()

    // Two positions, deliberately kept apart. PDFKit writes a line break into
    // the page's text at the end of every line, and those breaks are not in the
    // space it numbers characters by - so the text's own index runs ahead of the
    // index a box is asked for by one per line. Reading them as the same number
    // pairs each line's characters with the boxes of the line before, which is
    // a page that comes out as letters in the wrong order.
    var boxIndex = 0

    text.forEachIndexed { position, character ->
        if (character.isLineBreak()) {
            glyphs += line.onOneBaseline()
            line = mutableListOf()

            return@forEachIndexed
        }

        if (boxIndex.toULong() >= numberOfCharacters) return@forEachIndexed

        val ink = characterBoundsAtIndex(boxIndex.toLong()).useContents {
            // A character with no box is one PDFKit knows about but the page
            // never drew - it has no place, so there is nothing to report.
            if (size.width <= 0.0 && size.height <= 0.0) {
                null
            } else {
                InkGlyph(
                    text = character,
                    left = (origin.x - originX).toFloat(),
                    right = (origin.x + size.width - originX).toFloat(),
                    baseline = (topEdge - origin.y).toFloat(),
                    inkHeight = size.height.toFloat(),
                    pointSize = fonts.at(position),
                )
            }
        }

        boxIndex++

        if (ink != null) line += ink
    }

    glyphs += line.onOneBaseline()

    return glyphs.takeIf { it.isNotEmpty() }
}

/** The turn at which a page's text still runs the same way its box does. */
private const val HALF_TURN = 180L

/** PDFKit's own separators, which stand for a line ending rather than a character. */
private fun Char.isLineBreak(): Boolean = this == '\n' || this == '\r'

/** One character as PDFKit describes it, before it is given a common height. */
private class InkGlyph(
    val text: Char,
    val left: Float,
    val right: Float,
    val baseline: Float,
    val inkHeight: Float,
    val pointSize: Float?,
)

/**
 * How tall a glyph is reported to be, as a proportion of the size its type was
 * set at.
 *
 * PDFKit alone reports the ink: an `e` comes back half as tall as an `l` beside
 * it, because that is how much of the page each marks. Every other reader
 * reports a box derived from the font, the same for every character of a line,
 * and the shared inference that turns glyphs into paragraphs is calibrated
 * against that - see `BLOCK_GAP_RATIO`.
 *
 * Left as ink, a page of prose reads as a page of headings: the tallest letter
 * of a line stands a third higher than the page's middling letter, which is the
 * whole of what marks a heading out. So the ink is replaced by the one height
 * the file states, and this is the proportion of it the other platforms report -
 * which is what makes the same book come apart into the same paragraphs on every
 * device.
 */
private const val GLYPH_HEIGHT_RATIO = 0.58f

/**
 * One line's characters, given the single height a line of type has.
 *
 * The baseline is taken as the middle value of where the characters sit rather
 * than the lowest, because a `p` hangs below the line and a handful of them
 * would otherwise drag the whole line down; almost every character rests exactly
 * on it, so the middle one is it.
 */
private fun List<InkGlyph>.onOneBaseline(): List<PageGlyph> {
    if (isEmpty()) return emptyList()

    val baseline = map { it.baseline }.sorted()[size / 2]

    // The type size where the file states one, and the tallest ink on the line
    // where it does not - which is the closest thing to a letter's full height
    // that a page without readable fonts offers.
    val height = mapNotNull { it.pointSize }.maxOrNull()?.times(GLYPH_HEIGHT_RATIO)
        ?: maxOf { it.inkHeight }

    return map { glyph ->
        PageGlyph(
            text = glyph.text.toString(),
            bounds = TextQuad(
                left = glyph.left,
                top = baseline - height,
                right = glyph.right,
                bottom = baseline,
            ),
        )
    }
}

/**
 * The size the type is set at, character by character.
 *
 * PDFKit will describe a page as an attributed string, which carries the font
 * every run of it was set in. Asked per character that would be a dictionary
 * built per character, so each answer is kept until the run it came from runs
 * out - which on a page of one typeface is a single question.
 */
@OptIn(ExperimentalForeignApi::class)
private class TypeSizes(private val described: NSAttributedString?) {

    private var runEnd = 0
    private var size: Float? = null

    fun at(position: Int): Float? {
        val described = described ?: return null

        if (position >= runEnd) {
            memScoped {
                val run = alloc<NSRange>()
                val attributes = described.attributesAtIndex(position.toULong(), run.ptr)

                size = (attributes[NSFontAttributeName] as? UIFont)?.pointSize?.toFloat()
                runEnd = (run.location + run.length).toInt()
            }
        }

        return size
    }
}
