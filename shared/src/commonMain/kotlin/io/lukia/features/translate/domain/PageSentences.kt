package io.lukia.features.translate.domain

import io.lukia.features.reader.domain.GlyphBlock
import io.lukia.features.reader.domain.PageGlyph
import io.lukia.features.reader.domain.PlacedGlyph
import io.lukia.features.reader.domain.TextQuad
import io.lukia.features.reader.domain.between
import io.lukia.features.reader.domain.intoBlocks
import io.lukia.features.reader.domain.intoLines
import io.lukia.features.reader.domain.placedText

/**
 * One sentence of a laid-out page: what it says, and the rectangles it covers.
 *
 * [quads] is one rectangle per line the sentence runs across, so a sentence that
 * wraps is two or three quads rather than one box swallowing the lines between
 * them. That is what lets the reader draw a translation over exactly the words
 * it replaces instead of over a paragraph-shaped hole.
 */
data class PageSentence(
    val index: Int,
    val text: String,
    val quads: List<TextQuad>,
    /**
     * The words it is made of, each with the part of the page it covers.
     *
     * Worked out here rather than left to whoever draws the page, because a word
     * on a page is not a thing that can be found from the sentence's text: what a
     * PDF records is glyphs and where they landed, and the only place the two are
     * still together is where the sentence itself was inferred. By the time this
     * has been reduced to a string and some rectangles, which rectangle covers
     * the fourth word is no longer answerable.
     */
    val words: List<PageWord> = emptyList(),
    /**
     * Which block of the page it was read out of.
     *
     * A PDF records no paragraphs - what counts as a block here is inferred from
     * the space left between lines, by
     * [io.lukia.features.reader.domain.intoBlocks] - but that inference is made
     * anyway, to stop a heading and the footnote under it from running into one
     * sentence, and keeping its answer is what lets the reader ask about a whole
     * paragraph of a page. Without it the grouping was worked out and thrown
     * away on every page.
     */
    val blockIndex: Int = 0,
) {

    /** The whole sentence's extent, for hit-testing and for placing a caption. */
    val bounds: TextQuad get() = quads.union()

    /** The word at a point inside this sentence, or null between two of them. */
    fun wordAt(x: Float, y: Float): PageWord? =
        words.firstOrNull { word -> word.quads.any { it.contains(x, y) } }
}

/** One word of a laid-out page: what it says, and the rectangles it covers. */
data class PageWord(
    /** Its place in the sentence, which is how it is addressed - see [Word]. */
    val index: Int,
    val text: String,
    val quads: List<TextQuad>,
) {

    val bounds: TextQuad get() = quads.union()
}

/**
 * Reads a page's sentences off the characters it is made of.
 *
 * The geometry - which glyphs share a line, where one block ends and the next
 * begins, where the spaces between words go - is
 * [io.lukia.features.reader.domain.PageBlocks]'s doing and is shared with
 * everything else that reads a laid-out page. What is left here is the part that
 * is about sentences: cutting the text into them, and keeping each one attached
 * to the part of the page it covers.
 */
fun pageSentences(glyphs: List<PageGlyph>): List<PageSentence> {
    val lines = glyphs.filter { it.text.isNotEmpty() }.intoLines()
    if (lines.isEmpty()) return emptyList()

    var next = 0

    // Read block by block rather than as one run of text down the page: a
    // sentence that ran from a heading into the paragraph under it would be one
    // passage made of two unrelated things.
    return lines.intoBlocks().flatMapIndexed { blockIndex, block ->
        block.sentences(from = next, blockIndex = blockIndex).also { next += it.size }
    }
}

/**
 * One block's sentences, numbered on from where the last block finished.
 *
 * Numbered across the page rather than from zero in each block, because that is
 * how a page addresses its sentences - see [TextAnchor.Page]. The block they
 * came out of travels beside the number rather than replacing it.
 */
private fun GlyphBlock.sentences(from: Int, blockIndex: Int): List<PageSentence> {
    val placed = placedText()

    return splitIntoSentences(placed.text).mapNotNull { sentence ->
        val glyphs = placed.owners.between(sentence.start, sentence.end)
        if (glyphs.isEmpty()) return@mapNotNull null

        PageSentence(
            index = from + sentence.index,
            text = sentence.text,
            quads = glyphs.intoQuads(),
            blockIndex = blockIndex,
            // The same offsets, cut finer. A word that breaks across a line is
            // two ribbons for the same reason a sentence is.
            words = sentence.words().mapNotNull { word ->
                placed.owners
                    .between(word.start, word.end)
                    .takeIf { it.isNotEmpty() }
                    ?.let { PageWord(index = word.index, text = word.text, quads = it.intoQuads()) }
            },
        )
    }
}

/**
 * One rectangle per line these glyphs were set on.
 *
 * A passage running from halfway along one line to halfway along the next covers
 * two ribbons of the page, not the block between their far corners - which is
 * what a single bounding box would give, and would put a mark over two half
 * lines it has nothing to do with.
 */
private fun List<PlacedGlyph>.intoQuads(): List<TextQuad> = groupBy { it.line }
    .entries
    .sortedBy { (line, _) -> line }
    .map { (_, onLine) -> onLine.map { placed -> placed.glyph.bounds }.union() }

/** The sentence at a point on the page, or null where there are no words. */
fun List<PageSentence>.sentenceAt(x: Float, y: Float): PageSentence? =
    firstOrNull { sentence -> sentence.quads.any { it.contains(x, y) } }

fun TextQuad.contains(x: Float, y: Float): Boolean =
    x >= left && x <= right && y >= top && y <= bottom

/** The smallest rectangle covering all of these. */
fun List<TextQuad>.union(): TextQuad = TextQuad(
    left = minOf { it.left },
    top = minOf { it.top },
    right = maxOf { it.right },
    bottom = maxOf { it.bottom },
)
