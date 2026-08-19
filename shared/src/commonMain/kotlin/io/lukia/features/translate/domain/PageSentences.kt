package io.lukia.features.translate.domain

import io.lukia.features.reader.domain.PageGlyph
import io.lukia.features.reader.domain.TextQuad

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
    val words: List<PageWord> = emptyList()
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
    val quads: List<TextQuad>
) {

    val bounds: TextQuad get() = quads.union()
}

/**
 * Reads a page's sentences off the characters it is made of.
 *
 * A PDF stores glyphs and positions, not words, lines or sentences - a page is
 * a set of instructions for putting marks in places, and everything a reader
 * thinks of as structure has to be inferred back out of where those marks
 * landed. That inference is here, in common code, so it is the same on every
 * platform and can be tested without a PDF: what each platform provides is only
 * the characters and their boxes.
 */
fun pageSentences(glyphs: List<PageGlyph>): List<PageSentence> {
    val lines = glyphs.filter { it.text.isNotEmpty() }.intoLines()
    if (lines.isEmpty()) return emptyList()

    var next = 0

    // Read block by block rather than as one run of text down the page. A PDF
    // records no paragraphs, so the only evidence of where one thing ends and
    // the next begins is the space left between them - and without using it, a
    // heading, its paragraph and the footnote under it join into a single
    // sentence that ends wherever the page happens to have its first full stop.
    return lines.intoBlocks().flatMap { block ->
        block.sentences(from = next).also { next += it.size }
    }
}

/** One block's sentences, numbered on from where the last block finished. */
private fun List<List<PageGlyph>>.sentences(from: Int): List<PageSentence> {
    val text = StringBuilder()

    // One entry per character of [text], naming the glyph it came from - or null
    // for a space this code inferred, which belongs to no glyph and so covers no
    // part of the page.
    val owners = mutableListOf<PlacedGlyph?>()

    fun append(part: String, owner: PlacedGlyph?) {
        text.append(part)
        repeat(part.length) { owners.add(owner) }
    }

    forEachIndexed { lineIndex, line ->
        // A word broken across a line break is one word. Joining the halves with
        // a space instead would hand the translator "exam- ple", which is two
        // things it has to guess were one.
        if (lineIndex > 0 && !text.endsWithHyphen()) append(" ", owner = null)

        val threshold = line.spaceThreshold()

        line.forEachIndexed { index, glyph ->
            val previous = line.getOrNull(index - 1)

            if (previous != null && previous.isSeparatedFrom(glyph, threshold)) {
                append(" ", owner = null)
            }

            append(glyph.text, PlacedGlyph(glyph, lineIndex))
        }
    }

    return splitIntoSentences(text.toString()).mapNotNull { sentence ->
        val placed = owners.between(sentence.start, sentence.end)
        if (placed.isEmpty()) return@mapNotNull null

        PageSentence(
            index = from + sentence.index,
            text = sentence.text,
            quads = placed.intoQuads(),
            // The same offsets, cut finer. A word that breaks across a line is
            // two ribbons for the same reason a sentence is.
            words = sentence.words().mapNotNull { word ->
                owners.between(word.start, word.end)
                    .takeIf { it.isNotEmpty() }
                    ?.let { PageWord(index = word.index, text = word.text, quads = it.intoQuads()) }
            }
        )
    }
}

/**
 * How far apart two lines have to be before they are separate things.
 *
 * Measured against the glyphs' own height, which is not a line's height: what a
 * PDF reports per glyph is roughly the cap height, and measurement against real
 * files puts that near 0.58 of the size the type was set at. So the gap between
 * one line's glyphs and the next is `leading - capHeight`, and this ratio works
 * out as `leading / fontSize / 0.58 - 1`:
 *
 *     leading 1.2x  ->  1.07     ordinary body text
 *     leading 1.5x  ->  1.59     loosely set body text
 *     leading 1.75x ->  2.02
 *     leading 2.0x  ->  2.45     a paragraph break, or the space under a heading
 *
 * Two is the gap between the two groups. It is deliberately nearer the second:
 * splitting a paragraph that was merely set loosely cuts a sentence in half
 * mid-thought, which the reader sees immediately, while failing to split two
 * blocks only leaves a longer passage.
 */
private const val BLOCK_GAP_RATIO = 2.0f

private fun List<List<PageGlyph>>.intoBlocks(): List<List<List<PageGlyph>>> {
    val blocks = mutableListOf<MutableList<List<PageGlyph>>>()

    forEach { line ->
        val previous = blocks.lastOrNull()?.lastOrNull()

        if (previous == null || line.followsOn(previous)) {
            if (previous == null) blocks.add(mutableListOf(line)) else blocks.last().add(line)
        } else {
            blocks.add(mutableListOf(line))
        }
    }

    return blocks
}

/** Whether this line reads as the next line of what came before it. */
private fun List<PageGlyph>.followsOn(previous: List<PageGlyph>): Boolean {
    val height = maxOf(
        maxOf { it.bounds.height },
        previous.maxOf { it.bounds.height }
    )

    val gap = minOf { it.bounds.top } - previous.maxOf { it.bounds.bottom }

    return gap <= height * BLOCK_GAP_RATIO
}

/** The glyphs that produced the characters between two offsets of the block's text. */
private fun List<PlacedGlyph?>.between(from: Int, to: Int): List<PlacedGlyph> =
    (from until to).mapNotNull { getOrNull(it) }

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
    bottom = maxOf { it.bottom }
)

private data class PlacedGlyph(val glyph: PageGlyph, val line: Int)

/**
 * How far apart two glyphs have to be before the gap is a word break.
 *
 * Measured against the line's own height rather than a fixed distance, because
 * a footnote and a chapter title are set at sizes that differ by a factor of
 * three and the space between their words differs with them.
 */
private const val SPACE_RATIO = 0.25f

private fun PageGlyph.isSeparatedFrom(next: PageGlyph, threshold: Float): Boolean {
    // A gap around a character that is already a space is not a second space.
    if (text.last().isWhitespace() || next.text.first().isWhitespace()) return false

    return next.bounds.left - bounds.right > threshold
}

private fun List<PageGlyph>.spaceThreshold(): Float =
    maxOf { it.bounds.height } * SPACE_RATIO

private fun StringBuilder.endsWithHyphen(): Boolean {
    val last = lastOrNull() ?: return false

    return last == '-' || last == '‐' || last == '­'
}

/**
 * The glyphs gathered into the lines they were set on, in reading order.
 *
 * By vertical overlap rather than by an exact baseline: a line holding a
 * superscript, a larger initial or a different face has glyphs whose tops and
 * bottoms do not agree, and grouping on equality would break every such line
 * into several. Two glyphs share a line when each one's middle falls inside the
 * other's height, which is true of everything set on one baseline and false of
 * the line above it.
 */
private fun List<PageGlyph>.intoLines(): List<List<PageGlyph>> {
    val byPosition = sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
    val lines = mutableListOf<MutableList<PageGlyph>>()

    byPosition.forEach { glyph ->
        val line = lines.lastOrNull()

        if (line != null && line.sharesLineWith(glyph)) {
            line.add(glyph)
        } else {
            lines.add(mutableListOf(glyph))
        }
    }

    return lines.map { line -> line.sortedBy { it.bounds.left } }
}

private fun List<PageGlyph>.sharesLineWith(glyph: PageGlyph): Boolean {
    val middle = glyph.bounds.top + glyph.bounds.height / 2

    return any { onLine ->
        val theirMiddle = onLine.bounds.top + onLine.bounds.height / 2

        middle >= onLine.bounds.top && middle <= onLine.bounds.bottom ||
            theirMiddle >= glyph.bounds.top && theirMiddle <= glyph.bounds.bottom
    }
}
