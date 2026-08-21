package io.lukia.features.reader.presentation.pagination

import androidx.compose.runtime.Immutable
import io.lukia.features.reader.domain.TextBlock

/**
 * One block of a book, or the part of one that fits on a page.
 *
 * [sectionIndex] and [blockIndex] say where the block lives in the book rather
 * than where it is on the page, because that is what has to stay true: a
 * translated sentence is anchored to the block it is in, and re-laying the book
 * out at a different type size must not move it. Nothing here is a position in
 * the paginated book - the pagination is a view of the book, not the book.
 *
 * [sentenceOffset] is how many of the block's sentences ran onto earlier pages,
 * so the third sentence of a paragraph is the third sentence of that paragraph
 * whichever page it was pushed onto. A block cut in the middle of a sentence
 * counts that sentence to both halves, which is the honest answer when half of
 * it really is on each.
 */
@Immutable
internal data class PageBlock(
    val sectionIndex: Int,
    val blockIndex: Int,
    val block: TextBlock,
    val sentenceOffset: Int = 0,
    /** True for a slice that carries on from the page before, which is not a new block. */
    val continued: Boolean = false,
    /**
     * True when this is the whole block rather than a piece of one.
     *
     * Which matters to whoever asks for a paragraph: a piece of one is not a
     * paragraph, and asking about it as though it were would file the answer
     * under the whole block - so the other piece, on the page before or after,
     * would show the same translation over its own different words.
     */
    val whole: Boolean = true,
)

/** One page of a reflowable book, as much of it as the window holds. */
@Immutable
internal data class TextPage(val blocks: List<PageBlock>) {

    /** The chapter a page belongs to is the one it starts in. */
    val sectionIndex: Int get() = blocks.firstOrNull()?.sectionIndex ?: 0
}

/**
 * A reflowable book laid out as pages, for one window and one type size.
 *
 * Every page count and every page number the reader is shown comes from here
 * rather than from the document: how long a book is in pages is a fact about
 * the window it is being read in, and the same book is a different number of
 * pages on a phone and on a monitor. What does not change is where anything is
 * in the book itself, which is why [pageOf] takes a place in the book and
 * returns a page rather than the other way round.
 */
@Immutable
internal class PaginatedBook(
    val pages: List<TextPage>,
    /** The page each of the book's sections opens on, by section index. */
    private val sectionPages: List<Int>,
) {

    val pageCount: Int get() = pages.size

    /**
     * The page holding a place in the book, for following the reader across a
     * re-layout and for the contents panel, whose entries point at sections.
     *
     * Clamped rather than absent for a section past the end: a book re-read at
     * a larger size has the same sections, and an entry that pointed at nothing
     * would be a contents row that does nothing when pressed.
     */
    fun pageOf(sectionIndex: Int, blockIndex: Int = 0): Int {
        val first = sectionPages.getOrNull(sectionIndex) ?: return pages.lastIndex.coerceAtLeast(0)

        // Forward from where the section starts rather than a search of the
        // book: a section is a handful of pages, and this is asked once per
        // contents row and on every re-layout.
        for (index in first until pages.size) {
            val page = pages[index]

            // The block first, and only then whether the section has been left
            // behind. A page can hold the end of one section and the start of
            // the next - an unnamed continuation runs straight on - and asking
            // about the next section first would step over a block that is on
            // this very page.
            if (page.blocks.any { it.sectionIndex == sectionIndex && it.blockIndex >= blockIndex }) {
                return index
            }

            if (page.blocks.all { it.sectionIndex > sectionIndex }) break
        }

        return first
    }

    /**
     * Where in the book the top of [pageIndex] is, which is what survives a
     * re-layout.
     *
     * The block the page opens with, even where the page opens partway through
     * it. That biases a re-layout very slightly backwards - to the start of the
     * paragraph the reader was in the middle of - which is the right way to be
     * wrong: it never skips text they have not read.
     */
    fun placeOf(pageIndex: Int): Pair<Int, Int> {
        val block = pages.getOrNull(pageIndex)?.blocks?.firstOrNull() ?: return 0 to 0

        return block.sectionIndex to block.blockIndex
    }

    companion object {
        val Empty = PaginatedBook(pages = emptyList(), sectionPages = emptyList())
    }
}
