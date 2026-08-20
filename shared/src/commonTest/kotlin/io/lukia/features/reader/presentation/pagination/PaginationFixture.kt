package io.lukia.features.reader.presentation.pagination

import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextRun
import io.lukia.features.reader.domain.TextSection

/**
 * A book whose blocks are exactly as tall as they say they are.
 *
 * One character of text is one pixel high, so a page of a hundred is a page of a
 * hundred characters, and every expectation in these tests can be counted rather
 * than measured. What is being checked here is the rule that fills a page - not
 * the font, which is [BlockMetrics]'s business and the platform's.
 */
internal const val PAGE_HEIGHT = 100

internal object CharacterHeights : BlockSizes {

    override val spacingPx: Int = 0

    override fun height(block: TextBlock): Int = blockText(block).length

    override fun fittingLength(block: TextBlock, heightPx: Int): Int =
        blockText(block).length.coerceAtMost(heightPx.coerceAtLeast(0))
}

internal fun paragraph(text: String): TextBlock = TextBlock.Paragraph(listOf(TextRun(text)))

internal fun heading(text: String): TextBlock =
    TextBlock.Heading(listOf(TextRun(text)), level = 1)

/** A block of [length] characters, for filling a page to a known amount. */
internal fun filler(length: Int, of: Char = 'x'): TextBlock = paragraph(of.toString().repeat(length))

internal fun chapter(title: String?, vararg blocks: TextBlock): TextSection =
    TextSection(title = title, blocks = blocks.toList())

internal fun pagesOf(vararg sections: TextSection): PaginatedBook =
    paginate(sections = sections.toList(), sizes = CharacterHeights, pageHeightPx = PAGE_HEIGHT)

/** Where each block on each page came from, which is what a page has to remember. */
internal fun PaginatedBook.places(): List<List<Pair<Int, Int>>> =
    pages.map { page -> page.blocks.map { it.sectionIndex to it.blockIndex } }
