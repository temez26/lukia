package io.lukia.features.reader.presentation.pagination

import androidx.compose.runtime.Immutable
import io.lukia.features.reader.domain.text.TextBlock

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
 * A place in the book itself: which block of which section.
 *
 * A fact about the book rather than about any layout of it, which is what makes
 * it the thing worth remembering. A page number is not: the same place is a
 * different page at a different type size, and a hundred pages further into a
 * long book on a phone than on a monitor.
 */
@Immutable
internal data class BookPlace(val sectionIndex: Int, val blockIndex: Int = 0)

/**
 * One run of a book's sections, laid out on pages of its own.
 *
 * The unit a book is laid out in - see `chunkStarts` for where the runs are cut
 * and why a cut there changes nothing. Not a book: [sectionPages] counts pages
 * from the start of this run rather than from the start of the book, so it means
 * something only alongside where the run begins.
 */
internal class PaginatedChunk(val pages: List<TextPage>, val sectionPages: List<Int>)

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
    /**
     * False while more of the book is still being laid out behind these pages.
     *
     * A reflowable book arrives a run of sections at a time - see
     * `paginateProgressively` - so what the reader is holding is often a true
     * beginning of a book rather than the whole of one. Everything that reads a
     * page number off this is right either way; the only thing that is not yet
     * settled is how many pages there turn out to be.
     */
    val complete: Boolean = true,
) {

    val pageCount: Int get() = pages.size

    /**
     * Whether this book has been laid out far enough to say where [place] is.
     *
     * The question [pageOf] cannot answer for itself: a book still being
     * measured knows nothing about the sections it has not reached, and
     * clamping those to its last page is right for a contents row - which has
     * to lead somewhere - and wrong for the reader's own position, which would
     * be moved to the end of a beginning and then recorded as having been
     * there. So the two callers are separated here rather than by making
     * [pageOf] answer differently to each of them.
     */
    fun holds(place: BookPlace): Boolean = place.sectionIndex in sectionPages.indices

    /**
     * The page holding a place in the book, for following the reader across a
     * re-layout and for the contents panel, whose entries point at sections.
     *
     * Clamped rather than absent for a section past the end: a book re-read at
     * a larger size has the same sections, and an entry that pointed at nothing
     * would be a contents row that does nothing when pressed.
     */
    fun pageOf(place: BookPlace): Int = pageOf(place.sectionIndex, place.blockIndex)

    /** The same, for a caller that holds the two halves apart - see [PagedOutline]. */
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
    fun placeOf(pageIndex: Int): BookPlace {
        val block = pages.getOrNull(pageIndex)?.blocks?.firstOrNull() ?: return BookPlace(0)

        return BookPlace(block.sectionIndex, block.blockIndex)
    }

    companion object {
        val Empty = PaginatedBook(pages = emptyList(), sectionPages = emptyList())
    }
}
