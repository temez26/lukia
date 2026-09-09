package io.lukia.features.reader.infrastructure.pdf

import io.lukia.features.reader.domain.text.TextBlock

/**
 * Every picture read out of a PDF, kept once and counted.
 *
 * Two jobs, both needing the whole book rather than one page.
 *
 * The first is the logo. A publisher's mark, a journal's banner, a watermark:
 * PDFs draw these on every page, and each is as much a picture to the file as a
 * photograph. Re-set into the text they are a logo between every two paragraphs
 * of a three-hundred page book. What gives them away is what gives a running
 * head away - they repeat - and repetition is only visible from above the page.
 *
 * The second is memory: that logo is three hundred copies of the same bytes, and
 * a book's plates are megabytes each. Pictures with identical bytes are kept
 * once and handed out as the same array, so a hundred pages carrying one banner
 * cost one banner - and past [MAX_KEPT_BYTES], no more are kept at all.
 */
internal class PdfFigures {

    /**
     * The pictures kept so far, by the hash of their bytes.
     *
     * A list per hash rather than one entry, because two different pictures can
     * hash alike - rarely, but a book of similar diagrams is exactly where it
     * would happen, and showing one diagram in place of another would be a bug
     * nobody could explain.
     */
    private val kept = mutableMapOf<Int, MutableList<ByteArray>>()

    /**
     * How many pages each picture appears on.
     *
     * Keyed by the kept array itself. A [ByteArray] compares by identity, which
     * is what makes this a map from one picture to its count rather than a map
     * that has to compare megabytes on every lookup.
     */
    private val pagesAppearedOn = mutableMapOf<ByteArray, Int>()

    private var keptBytes = 0

    /**
     * One page's blocks, with every picture on it replaced by the copy already
     * held - and any the book has no more room for taken out.
     *
     * Given the blocks rather than the pictures, so it runs after a page is read
     * rather than during. That is what lets the pages be read in parallel: this
     * is the one part of the conversion that has to see the book in order, and
     * keeping it here means the rest does not - see `readPdfAsText`.
     *
     * A picture drawn twice on the same page counts once: what is being counted
     * is the pages it appears on, and a logo in both corners of one page is not
     * evidence of anything.
     */
    fun onPage(blocks: List<TextBlock>): List<TextBlock> {
        val seenHere = mutableSetOf<ByteArray>()

        return blocks.mapNotNull { block ->
            val figure = block as? TextBlock.Figure ?: return@mapNotNull block

            intern(figure.bytes)?.let { canonical -> figure.kept(canonical, seenHere) }
        }
    }

    /** This picture as the copy being held, counted against the page it is on. */
    private fun TextBlock.Figure.kept(
        canonical: ByteArray,
        seenHere: MutableSet<ByteArray>,
    ): TextBlock.Figure {
        if (seenHere.add(canonical)) {
            pagesAppearedOn[canonical] = (pagesAppearedOn[canonical] ?: 0) + 1
        }

        if (canonical === bytes) return this

        return TextBlock.Figure(bytes = canonical, description = description)
    }

    /**
     * Whether this picture belongs to the pages rather than to the book, which
     * is to say it appears on too many of them to be an illustration.
     *
     * Two conditions, because either alone is wrong on a short document: a
     * two-page leaflet with the same photograph on both pages is not a
     * watermark, and a book where a diagram happens to appear three times is not
     * one either.
     */
    fun isPageFurniture(figure: TextBlock.Figure, pageCount: Int): Boolean {
        val appearances = pagesAppearedOn[figure.bytes] ?: return false

        return appearances >= MIN_REPEATS && appearances * 2 > pageCount
    }

    /**
     * The copy of this picture that is being kept, or null once the book has
     * had all the pictures it is going to get.
     *
     * A picture already held costs nothing to hand out again, so the budget only
     * ever turns away a new one.
     */
    private fun intern(bytes: ByteArray): ByteArray? {
        val alike = kept.getOrPut(bytes.contentHashCode()) { mutableListOf() }

        alike.firstOrNull { it.contentEquals(bytes) }?.let { return it }

        if (keptBytes + bytes.size > MAX_KEPT_BYTES) return null

        keptBytes += bytes.size
        alike.add(bytes)

        return bytes
    }

    private companion object {
        const val MIN_REPEATS = 3

        /**
         * How much of a book's pictures are carried into the text.
         *
         * The whole of a re-set book is held in memory while it is open, its
         * pictures included, and unlike its words they are megabytes each. A
         * book of plates would otherwise be a book that cannot be opened on a
         * phone - so past this the words still arrive and the pictures stop,
         * which is the half of the book somebody asked for text to read.
         */
        const val MAX_KEPT_BYTES = 32 * 1024 * 1024
    }
}
