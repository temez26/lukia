package io.lukia.features.library

import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.features.library.presentation.model.HomeLayout
import io.lukia.features.library.presentation.model.HomeShelf
import io.lukia.features.library.presentation.model.homeLayout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** How the mobile home screen groups a library into the book being read and the rows under it. */
class HomeShelvesTest {

    private fun readAt(stamp: Long) =
        ReadingProgress(pageIndex = 20, pageCount = 100, readAt = stamp)

    private fun layoutOf(vararg books: TestBook, limit: Int = 12) =
        library(*books).homeLayout(limit)

    private fun rowsOf(vararg books: TestBook, limit: Int = 12) = rows(layoutOf(*books, limit = limit))

    private fun rows(home: HomeLayout) = home.rows.associate { it.shelf to it.books.titles() }

    @Test
    fun theCurrentBookIsTheMostRecentlyReadOneStillBeingRead() {
        val home = layoutOf(
            book("Older", progress = readAt(10)),
            book("Never Opened"),
            book("Newer", progress = readAt(20)),
        )

        assertEquals(listOf("Newer"), listOfNotNull(home.current).titles())
    }

    /** Carrying on is the card's whole point, and a finished book has nowhere to carry on to. */
    @Test
    fun aFinishedBookIsNeverTheCurrentOne() {
        val home = layoutOf(
            book("Done", progress = finished()),
            book("Never Opened"),
        )

        assertNull(home.current)
    }

    @Test
    fun alsoReadingHoldsTheOtherBooksBeingRead_mostRecentlyReadFirst() {
        val rows = rowsOf(
            book("Oldest", progress = readAt(10)),
            book("Newest", progress = readAt(30)),
            book("Middle", progress = readAt(20)),
        )

        assertEquals(listOf("Middle", "Oldest"), rows[HomeShelf.Reading])
    }

    /**
     * The book being read is usually also the newest, and the same cover three
     * times down one screen reads as clutter rather than emphasis.
     */
    @Test
    fun eachBookAppearsOnce_onTheFirstPlaceThatHoldsIt() {
        val home = layoutOf(
            book("Unread Old"),
            book("Started", progress = readAt(10)),
            book("Unread New"),
        )

        val everyTitle = listOfNotNull(home.current).titles() + home.rows.flatMap { it.books.titles() }

        assertEquals(everyTitle.distinct(), everyTitle)
        assertEquals(listOf("Unread New", "Unread Old"), rows(home)[HomeShelf.RecentlyAdded])
    }

    @Test
    fun recentlyAddedIsTheImportOrderReversed() {
        val rows = rowsOf(book("First"), book("Second"), book("Third"))

        assertEquals(listOf("Third", "Second", "First"), rows[HomeShelf.RecentlyAdded])
    }

    @Test
    fun unreadHoldsWhatHasNeverBeenOpenedAndIsNotAlreadyShown() {
        val rows = rowsOf(
            book("Oldest Arrival"),
            book("Older Arrival"),
            book("Newer Arrival"),
            limit = 2,
        )

        assertEquals(listOf("Oldest Arrival"), rows[HomeShelf.Unread])
    }

    /** A heading over a blank strip teaches nothing. */
    @Test
    fun aRowWithNothingOnItIsLeftOutEntirely() {
        val rows = rowsOf(book("Unread And Unloved"))

        assertEquals(setOf(HomeShelf.RecentlyAdded), rows.keys)
    }

    @Test
    fun anEmptyLibraryHasNothingToShow() {
        assertTrue(library().homeLayout().isEmpty)
    }

    /** Past a rowful, the way to the rest is the row's title rather than a longer flick. */
    @Test
    fun eachRowStopsAtItsLimit() {
        val books = (1..5).map { book("Book $it") }.toTypedArray()

        val rows = rowsOf(*books, limit = 3)

        assertEquals(listOf("Book 5", "Book 4", "Book 3"), rows[HomeShelf.RecentlyAdded])
    }
}
