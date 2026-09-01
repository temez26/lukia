package io.lukia.features.reader.presentation.pagination

import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.domain.outline.ReaderOutline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Finding a place in a book that has been laid out - which is what the contents
 * panel does, and what keeps the reader where they were when the type size
 * changes and every page number in the book moves.
 */
class PaginatedBookTest {

    private val book = pagesOf(
        chapter("One", filler(60), filler(60), filler(60)),
        chapter("Two", filler(60)),
    )

    @Test
    fun aChapterIsFoundAtThePageItOpensOn() {
        assertEquals(0, book.pageOf(sectionIndex = 0))
        assertEquals(3, book.pageOf(sectionIndex = 1))
    }

    @Test
    fun aBlockIsFoundOnThePageThatHoldsIt() {
        assertEquals(0, book.pageOf(sectionIndex = 0, blockIndex = 0))
        assertEquals(1, book.pageOf(sectionIndex = 0, blockIndex = 1))
        assertEquals(2, book.pageOf(sectionIndex = 0, blockIndex = 2))
    }

    /** What the reader was looking at, in terms the next layout will understand. */
    @Test
    fun everyPageSaysWhereInTheBookItStarts() {
        assertEquals(
            listOf(BookPlace(0, 0), BookPlace(0, 1), BookPlace(0, 2), BookPlace(1, 0)),
            book.pages.indices.map(book::placeOf),
        )
    }

    /**
     * The whole point of addressing a place rather than a page: the same block
     * is on a different page once the book is set larger, and that is where the
     * reader has to be put.
     */
    @Test
    fun aPlaceSurvivesTheBookBeingLaidOutAgain() {
        val place = book.placeOf(2)

        val larger = paginate(
            sections = listOf(
                chapter("One", filler(60), filler(60), filler(60)),
                chapter("Two", filler(60)),
            ),
            sizes = CharacterHeights,
            pageHeightPx = PAGE_HEIGHT / 2,
        )

        assertEquals(BookPlace(0, 2), place)

        // On the page rather than at the top of it: laid out smaller, that block
        // starts partway down a page that opens with the one before it. What
        // must not happen is the reader being sent to a page without it.
        val landed = larger.pages[larger.pageOf(place)]

        assertTrue(landed.blocks.any { it.sectionIndex == 0 && it.blockIndex == 2 })
    }

    /** A contents row that leads nowhere is a row that does nothing when pressed. */
    @Test
    fun aChapterPastTheEndOfTheBookLandsOnTheLastPageThereIs() {
        assertEquals(book.pages.lastIndex, book.pageOf(sectionIndex = 9))
    }

    @Test
    fun theContentsIsMovedOntoThePagesTheReaderTurns() {
        val outline = ReaderOutline.Chapters(
            listOf(
                OutlineEntry(title = "One", pageIndex = 0, level = 0),
                OutlineEntry(title = "Two", pageIndex = 1, level = 0),
            ),
        )

        val paged = outline.onPages(book, reflowable = true) as ReaderOutline.Chapters

        assertEquals(listOf(0, 3), paged.entries.map { it.pageIndex })
        assertEquals(listOf("One", "Two"), paged.entries.map { it.title })
    }

    /** A PDF's outline already points at its own pages; there is no layout between. */
    @Test
    fun aFixedLayoutDocumentsContentsIsLeftAlone() {
        val outline = ReaderOutline.Chapters(
            listOf(OutlineEntry(title = "One", pageIndex = 7, level = 0)),
        )

        val paged = outline.onPages(book, reflowable = false) as ReaderOutline.Chapters

        assertEquals(listOf(7), paged.entries.map { it.pageIndex })
    }

    /** Nothing to point at yet: the book has not been laid out. */
    @Test
    fun theContentsIsLeftAloneUntilThereArePagesToPointAt() {
        val outline = ReaderOutline.Chapters(
            listOf(OutlineEntry(title = "One", pageIndex = 3, level = 0)),
        )

        val paged = outline.onPages(PaginatedBook.Empty, reflowable = true) as ReaderOutline.Chapters

        assertEquals(listOf(3), paged.entries.map { it.pageIndex })
    }

    /**
     * The whole point of an outline carrying a block as well as a section: a
     * book names its chapters by file and its sections by an anchor inside one,
     * so several entries share a section and must not share a page.
     */
    @Test
    fun sectionsOfOneChapterLandOnTheirOwnPages() {
        val outline = ReaderOutline.Chapters(
            listOf(
                OutlineEntry(title = "Chapter", pageIndex = 0, level = 0, blockIndex = 0),
                OutlineEntry(title = "Second part", pageIndex = 0, level = 1, blockIndex = 1),
                OutlineEntry(title = "Third part", pageIndex = 0, level = 1, blockIndex = 2),
            ),
        )

        val paged = outline.onPages(book, reflowable = true) as ReaderOutline.Chapters

        assertEquals(listOf(0, 1, 2), paged.entries.map { it.pageIndex })
    }

    /**
     * A page can hold the end of one section and the start of the next, because
     * an unnamed continuation runs straight on. A block on such a page belongs
     * to the page it is on, not to where its section began.
     */
    @Test
    fun aBlockOnAPageSharedWithTheNextSectionIsFoundOnThatPage() {
        val shared = pagesOf(
            chapter("One", filler(60), filler(50)),
            chapter(null, filler(10)),
        )

        // Page 1 holds the second block of section 0 and all of section 1.
        assertEquals(listOf(listOf(0 to 0), listOf(0 to 1, 1 to 0)), shared.places())
        assertEquals(1, shared.pageOf(sectionIndex = 0, blockIndex = 1))
    }

    /** A block past the end of its section falls back to where the section starts. */
    @Test
    fun aBlockThatIsNotThereLandsAtTheStartOfItsSection() {
        assertEquals(book.pageOf(1), book.pageOf(sectionIndex = 1, blockIndex = 99))
    }
}
