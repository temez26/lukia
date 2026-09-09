package io.lukia.features.reader.domain.page

/**
 * The structure a laid-out page does not record, read back out of where its
 * marks landed.
 *
 * A PDF is a set of instructions for putting glyphs in places. Lines,
 * paragraphs and the spaces between words are not in the file at all - every one
 * of them has to be inferred from geometry, and the inference is the same
 * whether it is being made to translate a sentence somebody pointed at or to
 * re-set the whole book as text. So it lives here, once, in common code, and
 * what each platform provides is only the characters and their boxes.
 *
 * Everything here works in the page's own units - see [TextQuad].
 */

/** One line of a page: the glyphs set on it, left to right. */
internal typealias GlyphLine = List<PageGlyph>

/**
 * One block of a page: consecutive lines with no more than ordinary leading
 * between them, which is as close as geometry comes to a paragraph.
 */
internal typealias GlyphBlock = List<GlyphLine>

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
internal fun List<PageGlyph>.intoLines(): List<GlyphLine> {
    val byPosition = sortedWith(DownThePage)
    val lines = mutableListOf<MutableList<PageGlyph>>()

    byPosition.forEach { glyph ->
        val line = lines.lastOrNull()

        if (line != null && line.sharesLineWith(glyph)) {
            line.add(glyph)
        } else {
            lines.add(mutableListOf(glyph))
        }
    }

    return lines.map { line -> line.sortedWith(AcrossTheLine) }
}

/**
 * Down the page and then across it, and along one line.
 *
 * Written out rather than built with `compareBy`, which takes what it is given
 * as [Comparable] and so boxes two floats per comparison - a few hundred
 * thousand of them on a page, and every page of the book comes through here.
 */
private val DownThePage = Comparator<PageGlyph> { left, right ->
    val down = left.bounds.top.compareTo(right.bounds.top)

    if (down != 0) down else left.bounds.left.compareTo(right.bounds.left)
}

private val AcrossTheLine =
    Comparator<PageGlyph> { left, right -> left.bounds.left.compareTo(right.bounds.left) }

/**
 * The lines gathered into blocks, cut wherever more space was left than one line
 * of the same type would leave.
 *
 * Without this a heading, its paragraph and the footnote under it join into one
 * run of text that ends wherever the page happens to have its first full stop.
 */
internal fun List<GlyphLine>.intoBlocks(): List<GlyphBlock> {
    val blocks = mutableListOf<MutableList<GlyphLine>>()

    forEach { line ->
        val previous = blocks.lastOrNull()?.lastOrNull()

        if (previous != null && line.followsOn(previous)) {
            blocks.last().add(line)
        } else {
            blocks.add(mutableListOf(line))
        }
    }

    return blocks
}

/**
 * A block written out as text, with each character remembering the glyph it came
 * from.
 *
 * The two travel together because everything above this needs both and neither
 * can be recovered from the other: the text is what a sentence is split out of
 * and what a translator is sent, and the glyphs are the only thing that can say
 * which part of the page a stretch of that text covers.
 *
 * [owners] holds one entry per character of [text] - null for a space this code
 * inferred, which belongs to no glyph and so covers no part of the page.
 */
internal class PlacedText(val text: String, val owners: List<PlacedGlyph?>)

/** One glyph, and which line of its block it was set on. */
internal data class PlacedGlyph(val glyph: PageGlyph, val line: Int)

/** The glyphs that produced the characters between two offsets of [PlacedText.text]. */
internal fun List<PlacedGlyph?>.between(from: Int, to: Int): List<PlacedGlyph> =
    (from until to).mapNotNull { getOrNull(it) }

internal fun GlyphBlock.placedText(): PlacedText {
    val owners = mutableListOf<PlacedGlyph?>()

    val text = written { part, glyph, line ->
        val owner = glyph?.let { PlacedGlyph(it, line) }

        repeat(part.length) { owners.add(owner) }
    }

    return PlacedText(text, owners)
}

/**
 * The same words, without the map back to the page underneath them.
 *
 * All that re-setting a PDF as text needs: whether a block is a heading, a
 * bullet or a paragraph are all questions about the characters. The owners are a
 * [PlacedGlyph] per character - a few hundred thousand on a long book -
 * allocated to be read once and dropped, which on a phone is most of what the
 * conversion costs the collector.
 *
 * They are still exactly what pointing at a sentence needs, which is why both
 * ways of writing a block out live here and share one set of rules about where
 * the spaces go.
 */
internal fun GlyphBlock.written(): String = written { _, _, _ -> }

/**
 * This block written out, reporting each piece and the glyph it came from.
 *
 * [onPart] is called with null wherever a space was inferred rather than set,
 * since a space this code decided on belongs to no glyph and so covers no part
 * of the page.
 */
private inline fun GlyphBlock.written(
    onPart: (part: String, glyph: PageGlyph?, line: Int) -> Unit,
): String {
    val text = StringBuilder()

    forEachIndexed { lineIndex, line ->
        // A word broken across a line break is one word. Joining the halves with
        // a space instead would hand the translator "exam- ple", which is two
        // things it has to guess were one.
        if (lineIndex > 0 && !text.endsWithHyphen()) {
            text.append(" ")
            onPart(" ", null, lineIndex)
        }

        val threshold = line.spaceThreshold()

        line.forEachIndexed { index, glyph ->
            val previous = line.getOrNull(index - 1)

            if (previous != null && previous.isSeparatedFrom(glyph, threshold)) {
                text.append(" ")
                onPart(" ", null, lineIndex)
            }

            text.append(glyph.text)
            onPart(glyph.text, glyph, lineIndex)
        }
    }

    return text.toString()
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

/** Whether this line reads as the next line of what came before it. */
private fun GlyphLine.followsOn(previous: GlyphLine): Boolean {
    val height = maxOf(
        maxOf { it.bounds.height },
        previous.maxOf { it.bounds.height },
    )

    val gap = minOf { it.bounds.top } - previous.maxOf { it.bounds.bottom }

    return gap <= height * BLOCK_GAP_RATIO
}

/**
 * How far apart two glyphs have to be before the gap is a word break.
 *
 * Measured against the line's own height rather than a fixed distance, because
 * a footnote and a chapter title are set at sizes that differ by a factor of
 * three and the space between their words differs with them.
 */
private const val SPACE_RATIO = 0.25f

private fun GlyphLine.spaceThreshold(): Float = maxOf { it.bounds.height } * SPACE_RATIO

private fun PageGlyph.isSeparatedFrom(next: PageGlyph, threshold: Float): Boolean {
    // A gap around a character that is already a space is not a second space.
    if (text.last().isWhitespace() || next.text.first().isWhitespace()) return false

    return next.bounds.left - bounds.right > threshold
}

private fun StringBuilder.endsWithHyphen(): Boolean {
    val last = lastOrNull() ?: return false

    return last == '-' || last == '‐' || last == '­'
}

private fun GlyphLine.sharesLineWith(glyph: PageGlyph): Boolean {
    val middle = glyph.bounds.top + glyph.bounds.height / 2

    return any { onLine ->
        val theirMiddle = onLine.bounds.top + onLine.bounds.height / 2

        middle >= onLine.bounds.top &&
            middle <= onLine.bounds.bottom ||
            theirMiddle >= glyph.bounds.top &&
            theirMiddle <= glyph.bounds.bottom
    }
}
