package io.lukia.features.reader.presentation.pagination

import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextSection
import kotlinx.coroutines.yield
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Lays a reflowable book out as pages that each fit the window.
 *
 * The rule a printed book follows: fill the page with whole blocks while the
 * next one fits, start a new page when it does not, open every chapter on a page
 * of its own. A block too tall for any page - the book's doing rather than the
 * reader's - is cut at a line, its second half opening the next page still
 * knowing which block of which chapter it is.
 *
 * [pageHeightPx] is the room a page has for text, inside whatever margin the
 * sheet is drawn with. Nothing here knows about the sheet, the margins or the
 * chrome above them; it is handed the space and fills it.
 */
internal fun paginate(
    sections: List<TextSection>,
    sizes: BlockSizes,
    pageHeightPx: Int,
): PaginatedBook {
    if (sections.isEmpty() || pageHeightPx <= 0) return PaginatedBook.Empty

    val builder = PageBuilder(sizes, pageHeightPx)

    // The same walk as [paginateChunk] without the breathing, which is the whole
    // difference between the two: this one is not sharing a thread with anybody.
    sections.forEachIndexed { index, section ->
        builder.startSection(index, newPage = section.title != null)

        section.blocks.forEachIndexed { blockIndex, block -> builder.add(blockIndex, block) }
    }

    val chunk = builder.build()

    return PaginatedBook(pages = chunk.pages, sectionPages = chunk.sectionPages)
}

/**
 * One run of a book's sections, laid out on pages of its own.
 *
 * The same rule as [paginate] applied to part of a book, so the parts can be
 * laid out concurrently and shown as they arrive. What makes that give the same
 * book back is where the runs are cut: a run begins at a section that opens a
 * page, so it starts empty exactly as a whole book does and nothing on its first
 * page was carried over from the run before. See `chunkStarts`.
 *
 * [firstSection] is where this run begins in the book, because a page has to be
 * able to say which section of the book its blocks came from, not which section
 * of the run.
 */
internal suspend fun paginateChunk(
    sections: List<TextSection>,
    sizes: BlockSizes,
    pageHeightPx: Int,
    firstSection: Int,
): PaginatedChunk {
    val builder = PageBuilder(sizes, pageHeightPx)
    val breather = Breather()

    sections.forEachIndexed { index, section ->
        // A chapter opens a page. A section with no name of its own is not a
        // chapter but the rest of one - the reader divides an over-long file
        // into several - and running straight on is what makes it read as one
        // chapter rather than as a page break in the middle of a sentence.
        builder.startSection(firstSection + index, newPage = section.title != null)

        section.blocks.forEachIndexed { blockIndex, block ->
            builder.add(blockIndex, block)
            breather.breathe()
        }
    }

    return builder.build()
}

/**
 * Gives the thread back every so often.
 *
 * A run is measured on the thread the app draws on wherever the platform allows
 * text to be laid out on no other - see [measuringContext] - and a chapter
 * measured there in one go is a chapter's worth of frames not drawn. A book with
 * no chapter breaks to cut at is one run and one freeze, which is why this is
 * inside the walk over the blocks rather than between the runs.
 *
 * On a clock rather than a count of blocks, because how long a block takes is a
 * fact about the device and the block: a paragraph and a page of preformatted
 * code are not the same work. Where the measuring has a thread to itself this
 * costs a comparison per block and yields to nobody.
 */
private class Breather(private val timeSource: TimeSource = TimeSource.Monotonic) {

    private var since = timeSource.markNow()

    suspend fun breathe() {
        if (since.elapsedNow() < BREATH) return

        since = timeSource.markNow()
        yield()
    }
}

/**
 * A small part of a frame rather than a whole one.
 *
 * A stutter is a frame that came late, by however long the thread was busy when
 * it was due. So this is the delay the measuring can impose on a page being
 * scrolled in, and wants to be short against the sixteen milliseconds a frame
 * has - not half of it. Scrolling a reflowable book is itself most of a frame's
 * work, since the pages coming into view are set as they arrive.
 *
 * The cost of a shorter one is the handover: a trip through the run loop and
 * some tens of microseconds - about a percent of the measuring at this size, and
 * nothing where the measuring has a thread to itself.
 */
private val BREATH = 2.milliseconds

/**
 * Fills one page at a time, and remembers where each section began.
 *
 * A class rather than a fold because filling a page is a small state machine -
 * what is on the page, how much room is left, which section is being read - and
 * the one thing it must never do is lose track of where a block came from.
 */
private class PageBuilder(
    private val sizes: BlockSizes,
    private val pageHeightPx: Int,
) {

    private val pages = mutableListOf<TextPage>()
    private val sectionPages = mutableListOf<Int>()

    private var current = mutableListOf<PageBlock>()
    private var usedPx = 0
    private var sectionIndex = 0

    fun startSection(index: Int, newPage: Boolean) {
        sectionIndex = index

        if (newPage) flush()

        // Where this section starts is where the page it is on starts, whether
        // that page was opened for it or was already being filled.
        sectionPages += pages.size
    }

    fun add(blockIndex: Int, block: TextBlock) {
        // The whole block's text, kept because a slice of it has to be able to
        // say which of the block's sentences it holds - and a slice can only
        // count its own.
        val text = blockText(block)

        var remaining: TextBlock = block
        var consumed = 0

        while (true) {
            // Measured once, and carried through every question asked of it
            // below.
            //
            // How tall a block is does not depend on where it lands; how much
            // room is left on the page does. So the height is read once and the
            // room re-read each time, rather than the block being measured
            // again for each question - which is what this did, four times over
            // for a block that fits and five for one that has to be cut.
            //
            // The measuring itself was cached, so what those four bought was not
            // four layouts. It was four AnnotatedStrings rebuilt from the
            // block's runs and four probes of the measurer's cache, each
            // comparing a whole paragraph character by character, to learn one
            // number that had not moved - and on iOS all of it on the thread
            // drawing the reader, which is the only thread that platform allows
            // text to be laid out on. See [measuringContext].
            val height = sizes.height(remaining)

            // A block that would fit a page of its own, but not what is left of
            // this one, ends the page here - which is what keeps a paragraph in
            // one piece wherever one can be.
            if (height > availablePx() && height <= pageHeightPx) breakPage()

            val cut = cutToFill(remaining, height)

            if (cut == null) {
                // Either it fits, or there is nothing in it to divide - a plate,
                // or a line of code longer than the screen. The second kind gets
                // a page to itself and is drawn scaled down to it.
                if (height > availablePx()) breakPage()

                place(blockIndex, remaining, height, Slice(text, consumed, cut = false))
                return
            }

            val (head, tail, length) = cut

            // The head is a block that did not exist a moment ago, so it is the
            // one thing here that has to be measured rather than remembered.
            place(blockIndex, head, sizes.height(head), Slice(text, consumed, cut = true))
            flush()

            remaining = tail
            consumed += length
        }
    }

    fun build(): PaginatedChunk {
        flush()

        return PaginatedChunk(pages = pages.toList(), sectionPages = sectionPages.toList())
    }

    /**
     * [block], already measured as [heightPx], cut to fill what is left of the
     * page - the head that fits, the tail that does not, and how many characters
     * the head took.
     *
     * Null when the block should go on as it is - it fits, or there is nothing
     * inside it to cut between. The room left is filled rather than wasted: a
     * block this size is a run of text with no end in sight, and a fresh page
     * would not make it fit either.
     */
    private fun cutToFill(block: TextBlock, heightPx: Int): Triple<TextBlock, TextBlock, Int>? {
        val available = availablePx()

        if (available <= 0 || heightPx <= available) return null

        val (head, tail) = block.cutAt(sizes.fittingLength(block, available)) ?: return null

        return Triple(head, tail, blockText(head).length)
    }

    /** [heightPx] is [block]'s own height, measured once by the caller. */
    private fun place(blockIndex: Int, block: TextBlock, heightPx: Int, slice: Slice) {
        if (current.isNotEmpty()) usedPx += sizes.spacingPx

        current += PageBlock(
            sectionIndex = sectionIndex,
            blockIndex = blockIndex,
            block = block,
            sentenceOffset = slice.sentenceOffset(),
            continued = slice.continued,
            whole = slice.whole,
        )

        usedPx += heightPx
    }

    /**
     * Where a piece of a block sits in the block it was cut out of.
     *
     * [text] is the whole block's text, which is what a piece has to be measured
     * against: a piece can only count its own sentences, and the book counts
     * them from the start of the paragraph.
     */
    private class Slice(private val text: String, private val consumed: Int, val cut: Boolean) {

        val continued: Boolean get() = consumed > 0

        /** Nothing before it and nothing after it is the whole paragraph. */
        val whole: Boolean get() = !continued && !cut

        fun sentenceOffset(): Int = if (consumed == 0) 0 else sentencesBefore(text, consumed)
    }

    private fun availablePx(): Int =
        pageHeightPx - usedPx - if (current.isEmpty()) 0 else sizes.spacingPx

    /**
     * Ends the page before a block that will not fit on it, carrying any
     * headings at its foot over with that block - a heading is set with what it
     * heads, as print does. Left at the bottom of a page, a section's title read
     * as the end of the section before it, and a chapter's "CHAPTER 7:" was
     * parted from its name.
     *
     * Not where the headings are all the page holds: moving them would leave an
     * empty page behind, and change nothing about what follows them.
     */
    private fun breakPage() {
        val trailing = current.takeLastWhile { it.block is TextBlock.Heading }

        if (trailing.isEmpty() || trailing.size == current.size) {
            flush()
            return
        }

        repeat(trailing.size) { current.removeAt(current.lastIndex) }
        flush()

        trailing.forEach { carried ->
            if (current.isNotEmpty()) usedPx += sizes.spacingPx
            current += carried
            usedPx += sizes.height(carried.block)
        }
    }

    private fun flush() {
        if (current.isEmpty()) return

        pages += TextPage(current.toList())
        current = mutableListOf()
        usedPx = 0
    }
}
