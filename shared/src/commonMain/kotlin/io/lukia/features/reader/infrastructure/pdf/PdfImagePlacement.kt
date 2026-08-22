package io.lukia.features.reader.infrastructure.pdf

import io.lukia.features.reader.domain.PageSize
import io.lukia.features.reader.domain.TextQuad
import kotlin.math.max
import kotlin.math.min

/**
 * A rectangle as a PDF states one: a corner and a size, measured upwards from
 * the bottom left of the page. Its own type so that nothing mistakes it for the
 * downward-measured [TextQuad] everything else here works in.
 */
internal class PdfRect(val x: Float, val y: Float, val width: Float, val height: Float)

/**
 * Where a picture drawn on a PDF page lands, in the space the rest of the page
 * is measured in.
 *
 * A PDF draws an image by transforming the unit square, so what the file states
 * is a matrix and what this needs is a rectangle: the translation is the corner
 * and the scale is the size, both in the page's own units and measured upwards
 * from the bottom left, which is how PDF measures and how nothing else here
 * does. What comes back is measured downwards from the top left of the page as
 * it is displayed - the space [TextQuad] is defined in, and the space the
 * glyphs are already reported in, which is what lets a picture be ordered among
 * the paragraphs it was printed between.
 *
 * [page] is the page's own box before any rotation is applied - not the shape it
 * is shown at - and [rotation] is the quarter-turns the file asks for, which a
 * scanner or a spreadsheet in landscape routinely does.
 *
 * The maths is common code rather than repeated per platform because it is the
 * same maths: every PDF library hands over the same matrix, and getting the
 * rotation wrong on one platform only would put a plate in a different place in
 * the same book on two devices.
 */
internal fun pdfImageBounds(drawn: PdfRect, page: PageSize, rotation: Int): TextQuad {
    // A matrix may scale negatively, which flips the image and puts its corner
    // at what is arithmetically the far end. The rectangle it covers is the
    // same either way.
    val left = min(drawn.x, drawn.x + drawn.width)
    val right = max(drawn.x, drawn.x + drawn.width)
    val bottom = min(drawn.y, drawn.y + drawn.height)
    val top = max(drawn.y, drawn.y + drawn.height)

    val first = displayed(left, bottom, page, rotation)
    val second = displayed(right, top, page, rotation)

    return TextQuad(
        left = min(first.first, second.first),
        top = min(first.second, second.second),
        right = max(first.first, second.first),
        bottom = max(first.second, second.second),
    )
}

/**
 * One point of the page, turned the way the page is shown and measured from its
 * top left.
 *
 * Each quarter-turn moves an edge rather than a corner, which is the way to
 * follow it: turned a quarter clockwise, the page's bottom edge becomes its left
 * edge, so how far along the bottom a point was is how far down it now is.
 */
private fun displayed(
    x: Float,
    y: Float,
    page: PageSize,
    rotation: Int,
): Pair<Float, Float> = when (((rotation % FULL_TURN) + FULL_TURN) % FULL_TURN) {
    QUARTER_TURN -> y to x
    HALF_TURN -> (page.width - x) to y
    THREE_QUARTER_TURN -> (page.height - y) to (page.width - x)
    else -> x to (page.height - y)
}

private const val QUARTER_TURN = 90
private const val HALF_TURN = 180
private const val THREE_QUARTER_TURN = 270
private const val FULL_TURN = 360

/**
 * How small a picture has to be before it is furniture rather than an
 * illustration.
 *
 * A third of an inch each way. Below that a PDF's images are rules, borders,
 * bullets, the dot on a logo and the single-pixel gradients a word processor
 * lays behind a table - hundreds of them in some files, none of them anything
 * anyone would want re-set into their book. It is also what stops the reader
 * from paying to decode and re-encode every one of them.
 */
private const val MIN_FIGURE_SIDE = 24f

/** Whether a picture this size is worth carrying into the text. */
internal fun TextQuad.isWorthShowing(): Boolean =
    width >= MIN_FIGURE_SIDE && height >= MIN_FIGURE_SIDE
