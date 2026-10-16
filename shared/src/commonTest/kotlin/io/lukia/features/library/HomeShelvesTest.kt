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
    fun theRowsRunFavoritesRecentlyOpenedUnreadRecentlyAdded() {
        val home = layoutOf(
            book("Kept", favorite = true),
            book("Current", progress = readAt(30)),
            book("Opened", progress = readAt(10)),
            book("Fresh"),
            book("Overflow"),
            limit = 1,
        )

        assertEquals(
            listOf(HomeShelf.Favorites, HomeShelf.RecentlyOpened, HomeShelf.Unread, HomeShelf.RecentlyAdded),
            home.rows.map { it.shelf },
        )
    }

    @Test
    fun favoritesHoldOnlyWhatWasMarked_newestFirst() {
        val rows = rowsOf(
            book("Kept", favorite = true),
            book("Ordinary"),
            book("Also Kept", favorite = true),
        )

        assertEquals(listOf("Also Kept", "Kept"), rows[HomeShelf.Favorites])
    }

    /** A book finished last night was opened as recently as one still being read. */
    @Test
    fun recentlyOpenedHoldsEveryOpenedBook_mostRecentlyReadFirst() {
        val rows = rowsOf(
            book("Current", progress = readAt(40)),
            book("Older", progress = readAt(10)),
            book("Done", progress = ReadingProgress(99, 100, readAt = 30)),
            book("Never Opened"),
        )

        assertEquals(listOf("Done", "Older"), rows[HomeShelf.RecentlyOpened])
    }

    /**
     * The book being read is often also a favourite and among the newest, and
     * the same cover several times down one screen reads as clutter.
     */
    @Test
    fun eachBookAppearsOnce_onTheFirstPlaceThatHoldsIt() {
        val home = layoutOf(
            book("Kept Unread", favorite = true),
            book("Started", progress = readAt(10)),
            book("Unread"),
        )

        val everyTitle = listOfNotNull(home.current).titles() + home.rows.flatMap { it.books.titles() }

        assertEquals(everyTitle.distinct(), everyTitle)
        assertEquals(listOf("Kept Unread"), rows(home)[HomeShelf.Favorites])
        assertEquals(listOf("Unread"), rows(home)[HomeShelf.Unread])
    }

    @Test
    fun unreadHoldsWhatHasNeverBeenOpened_newestFirst() {
        val rows = rowsOf(
            book("Older Arrival"),
            book("Started", progress = readAt(10)),
            book("Newer Arrival"),
        )

        assertEquals(listOf("Newer Arrival", "Older Arrival"), rows[HomeShelf.Unread])
    }

    /** Last, so it holds only the arrivals no row above already shows. */
    @Test
    fun recentlyAddedHoldsWhatIsLeftOver_newestFirst() {
        val rows = rowsOf(book("First"), book("Second"), book("Third"), limit = 1)

        assertEquals(listOf("Second"), rows[HomeShelf.RecentlyAdded])
    }

    /** A heading over a blank strip teaches nothing. */
    @Test
    fun aRowWithNothingOnItIsLeftOutEntirely() {
        val rows = rowsOf(book("Unread And Unloved"))

        assertEquals(setOf(HomeShelf.Unread), rows.keys)
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

        assertEquals(listOf("Book 5", "Book 4", "Book 3"), rows[HomeShelf.Unread])
    }
}
