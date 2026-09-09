package io.lukia.features.reader.infrastructure.pdf

import io.lukia.features.reader.domain.page.GlyphBlock
import io.lukia.features.reader.domain.page.GlyphLine
import io.lukia.features.reader.domain.page.PageGlyph
import io.lukia.features.reader.domain.page.PageImage
import io.lukia.features.reader.domain.page.PageSize
import io.lukia.features.reader.domain.page.TextQuad
import io.lukia.features.reader.domain.page.intoBlocks
import io.lukia.features.reader.domain.page.intoLines
import io.lukia.features.reader.domain.page.written
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextRun

/**
 * One page of a PDF, read as the things written on it rather than as marks in
 * places.
 *
 * Everything here is inference, because a PDF states none of it: the file holds
 * glyphs and positions, and that a run of them is a heading, that the line alone
 * at the foot is a page number, that two close lines are one paragraph all have
 * to be read back out of size and position - and are all sometimes wrong.
 *
 * That is the trade a reader makes by asking for a PDF as text, and the rules
 * below take the side of keeping words: a missed heading reads as a paragraph,
 * while a paragraph dropped as furniture is text the reader came for and cannot
 * see. So each rule is deliberately narrow.
 */
internal fun pdfPageBlocks(
    glyphs: List<PageGlyph>,
    images: List<PageImage>,
    page: PageSize,
): List<TextBlock> {
    val lines = glyphs.filter { it.text.isNotEmpty() }.intoLines()
    val bodyHeight = lines.bodyHeight()

    val written = lines.intoBlocks()
        .flatMap { block -> block.splitAtSizeChanges() }
        .flatMap { block -> block.splitAtBullets() }
        .mapNotNull { block ->
            block.asTextBlock(bodyHeight, page)?.let { Placed(block.bounds(), it) }
        }

    val drawn = images
        .filterNot { it.bounds.isPageBackground(page, pageHasText = lines.isNotEmpty()) }
        .map { Placed(it.bounds, TextBlock.Figure(bytes = it.bytes, description = null)) }

    // Down the page and then across it, which is the only reading order a PDF
    // offers: the file states none, and the order its instructions happen to be
    // written in is the order a program drew them, not the order anybody reads
    // them. It is also the order the lines were already put in.
    return (written + drawn)
        .sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        .map { it.block }
}

/** One thing on the page, and where it was to put it in order. */
private class Placed(val bounds: TextQuad, val block: TextBlock)

private fun GlyphBlock.bounds(): TextQuad = TextQuad(
    left = minOf { line -> line.minOf { it.bounds.left } },
    top = minOf { line -> line.minOf { it.bounds.top } },
    right = maxOf { line -> line.maxOf { it.bounds.right } },
    bottom = maxOf { line -> line.maxOf { it.bounds.bottom } },
)

/**
 * How much of the page an image has to cover before it is the page rather than
 * a picture on it.
 */
private const val BACKGROUND_COVERAGE = 0.92f

/**
 * Whether this picture is the sheet the page was printed on.
 *
 * A watermark, a scanned background, a letterhead's tint block: something drawn
 * the size of the paper with the words on top of it. Re-set into the text it is
 * a full-page image between two paragraphs, which is both wrong and the largest
 * thing on the screen.
 *
 * Only where the page has words on it. A page that is nothing but one full-page
 * image is a plate - the frontispiece, a map, a photograph given its own leaf -
 * and that is a picture somebody wants.
 */
private fun TextQuad.isPageBackground(page: PageSize, pageHasText: Boolean): Boolean {
    if (!pageHasText || page.width <= 0f || page.height <= 0f) return false

    return width >= page.width * BACKGROUND_COVERAGE &&
        height >= page.height * BACKGROUND_COVERAGE
}

/**
 * The size the page's ordinary text is set at, as the middle of every glyph on
 * it.
 *
 * The median rather than a mode or an average: a page is mostly body text, so
 * its middle is body text however many headings and page numbers surround it,
 * and unlike an average one huge title cannot drag it.
 *
 * Per page rather than per document, which is what makes it affordable - the
 * alternative holds every glyph of every page to measure them together. It costs
 * the pages that are nothing but large type, where the median is that type and
 * nothing reads as a heading; those pages have no body text for a heading to
 * stand out from anyway.
 */
private fun List<GlyphLine>.bodyHeight(): Float {
    // Into an array of numbers rather than through a chain of lists. Every page
    // of the book asks this of every glyph on it, and the readable spelling of
    // it - flatten, map, filter, sort - is four lists of a few thousand boxed
    // floats built to read one of them and drop the rest.
    var measured = 0

    forEach { line -> line.forEach { if (it.bounds.height > 0f) measured++ } }

    if (measured == 0) return 1f

    val heights = FloatArray(measured)
    var at = 0

    forEach { line ->
        line.forEach { glyph ->
            val height = glyph.bounds.height

            if (height > 0f) heights[at++] = height
        }
    }

    heights.sort()

    return heights[measured / 2]
}

/**
 * How much larger than the body text a block has to be set before it is read as
 * a heading, and where the levels fall between.
 *
 * The steps match what the reader draws a heading of each level at - see
 * `headingStyle` - so a title measured at half as large again comes back set
 * half as large again.
 */
private const val HEADING_RATIO = 1.15f
private const val LEVEL_ONE_RATIO = 1.5f
private const val LEVEL_TWO_RATIO = 1.28f

/** Past this a run of large type is a pull quote or a poster, not a title. */
private const val HEADING_MAX_CHARS = 120
private const val HEADING_MAX_LINES = 3

private fun GlyphBlock.asTextBlock(bodyHeight: Float, page: PageSize): TextBlock? {
    val text = written().trim()
    val ratio = height() / bodyHeight

    if (text.isBlank() || isPageFurniture(page, text, ratio)) return null

    val marker = bulletMarker(text)

    val heading = ratio >= HEADING_RATIO &&
        size <= HEADING_MAX_LINES &&
        text.length <= HEADING_MAX_CHARS

    return when {
        marker != null -> TextBlock.ListItem(
            text = listOf(TextRun(text.removePrefix(marker).trimStart())),
            marker = "•",
        )

        heading -> TextBlock.Heading(text = listOf(TextRun(text)), level = headingLevel(ratio))

        else -> TextBlock.Paragraph(text = listOf(TextRun(text)))
    }
}

/** Past the third, a heading is set at one size whatever its depth. */
private const val SMALLEST_HEADING_LEVEL = 3

private fun headingLevel(ratio: Float): Int = when {
    ratio >= LEVEL_ONE_RATIO -> 1
    ratio >= LEVEL_TWO_RATIO -> 2
    else -> SMALLEST_HEADING_LEVEL
}

/** How large this block's type is, taken from its tallest glyph. */
private fun GlyphBlock.height(): Float = maxOf { line -> line.maxOf { it.bounds.height } }

/**
 * How near an edge of the page a line has to sit before it is read as belonging
 * to the page rather than to the book.
 *
 * Six percent of the height, which is inside the margin of a normally set page
 * - a running head sits above the text block, not at the top of it. Body text
 * that begins unusually high is protected by the other two conditions: furniture
 * is one short line, set no larger than the body.
 */
private const val MARGIN_BAND = 0.06f
private const val FURNITURE_MAX_CHARS = 80

/** A block that is the page's own labelling: a running head, a folio, a footer. */
private fun GlyphBlock.isPageFurniture(page: PageSize, text: String, ratio: Float): Boolean {
    if (page.height <= 0f || size > 1) return false
    if (text.length > FURNITURE_MAX_CHARS || ratio > HEADING_RATIO) return false

    val band = page.height * MARGIN_BAND
    val top = minOf { line -> line.minOf { it.bounds.top } }
    val bottom = maxOf { line -> line.maxOf { it.bounds.bottom } }

    return bottom <= band || top >= page.height - band
}

/** The characters a book sets a list item with. A hyphen is deliberately absent. */
private val BulletMarkers = listOf("•", "◦", "▪", "‣", "·", "–", "—")

/**
 * The marker this text is bulleted with, or null when it is not a list item.
 *
 * A dash counts only where the page set it as a bullet, which is to say with a
 * space after it: a line opening "—she said" is dialogue. A hyphen is never a
 * bullet, because a line beginning with one is far more often a word broken
 * across a page.
 */
private fun bulletMarker(text: String): String? =
    BulletMarkers.firstOrNull { text.startsWith("$it ") }

/**
 * A block cut wherever the type changes size.
 *
 * The gap rule that made these blocks measures the space between lines against
 * their own height, so a heading set close above its paragraph takes the
 * paragraph with it - its larger type widens the very threshold that should have
 * separated them. A change of size between one line and the next is the clearer
 * signal, and the one a reader's eye uses.
 */
private fun GlyphBlock.splitAtSizeChanges(): List<GlyphBlock> {
    val parts = mutableListOf<MutableList<GlyphLine>>()

    forEach { line ->
        val previous = parts.lastOrNull()?.lastOrNull()
        val changed = previous != null && line.lineHeight().differsInSizeFrom(previous.lineHeight())

        if (changed || parts.isEmpty()) parts.add(mutableListOf(line)) else parts.last().add(line)
    }

    return parts
}

/** Named apart from the block's own height: the two are the same JVM signature. */
private fun GlyphLine.lineHeight(): Float = maxOf { it.bounds.height }

private fun Float.differsInSizeFrom(other: Float): Boolean {
    val larger = maxOf(this, other)
    val smaller = minOf(this, other)

    return smaller > 0f && larger / smaller >= HEADING_RATIO
}

/**
 * Whether it is worth setting this line out in order to ask whether it opens a
 * list item.
 *
 * The question below costs the line's whole text and is asked of every line in
 * the book, where all but a handful are prose. A list item begins with its
 * marker, so the marker has to be the line's first character; if it is not one,
 * nothing that follows can make the line a bullet.
 *
 * Never wrong in the direction that matters: a line turned away here could not
 * have started with a marker in its written text either, since the inferred
 * spaces are trimmed off the front just as the blank glyphs are skipped here.
 */
private fun GlyphLine.mayOpenAnItem(): Boolean {
    val opening = firstOrNull { it.text.isNotBlank() }?.text?.trimStart() ?: return false

    return BulletMarkers.any { opening.startsWith(it) }
}

/**
 * A block cut at every line that opens a new bullet.
 *
 * A list set with ordinary leading is one block to the geometry - the lines are
 * no further apart than a paragraph's - so without this a list of six points
 * comes back as one paragraph with bullets buried in the middle of it.
 */
private fun GlyphBlock.splitAtBullets(): List<GlyphBlock> {
    val parts = mutableListOf<MutableList<GlyphLine>>()

    forEach { line ->
        val opensAnItem = line.mayOpenAnItem() &&
            bulletMarker(listOf(line).written().trimStart()) != null

        if (opensAnItem || parts.isEmpty()) parts.add(mutableListOf(line)) else parts.last().add(line)
    }

    return parts
}
