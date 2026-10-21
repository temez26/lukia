package io.lukia.features.library

import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.features.library.presentation.model.HomeLayout
import io.lukia.features.library.presentation.model.HomeShelf
import io.lukia.features.library.presentation.model.homeLayout
import io.lukia.features.library.presentation.model.onShelf
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
    fun theRowsRunRecentlyOpenedFavoritesRecentlyAddedUnread() {
        val home = layoutOf(
            book("Kept", favorite = true),
            book("Current", progress = readAt(30)),
            book("Opened", progress = readAt(10)),
            book("Fresh"),
            book("Overflow"),
            limit = 1,
        )

        assertEquals(
            listOf(HomeShelf.RecentlyOpened, HomeShelf.Favorites, HomeShelf.RecentlyAdded, HomeShelf.Unread),
            home.rows.map { it.shelf },
        )
    }

    /** The list shows three; a fourth opened book is left for the shelves after it, not hidden. */
    @Test
    fun recentlyOpenedHoldsOnlyWhatItsListShows() {
        val home = layoutOf(
            book("Current", progress = readAt(50)),
            book("A", progress = readAt(40)),
            book("B", progress = readAt(30)),
            book("C", progress = readAt(20)),
            book("D", progress = readAt(10), favorite = true),
        )

        assertEquals(listOf("A", "B", "C"), rows(home)[HomeShelf.RecentlyOpened])
        assertEquals(listOf("D"), rows(home)[HomeShelf.Favorites])
    }

    /** A row's count is the whole shelf's, not the handful the row has room for. */
    @Test
    fun aRowCountsEveryBookItsShelfHolds() {
        val home = layoutOf(
            book("Current", progress = readAt(50)),
            book("A", progress = readAt(40)),
            book("B", progress = readAt(30)),
            book("C", progress = readAt(20)),
            book("D", progress = readAt(10)),
        )

        assertEquals(5, home.rows.single { it.shelf == HomeShelf.RecentlyOpened }.total)
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
        // New and unread, it is shown with the arrivals rather than the pile.
        assertEquals(listOf("Unread"), rows(home)[HomeShelf.RecentlyAdded])
        assertNull(rows(home)[HomeShelf.Unread])
    }

    @Test
    fun unreadHoldsWhatHasNeverBeenOpened_newestFirst() {
        val books = library(
            book("Older Arrival"),
            book("Started", progress = readAt(10)),
            book("Newer Arrival"),
        )

        assertEquals(listOf("Newer Arrival", "Older Arrival"), books.onShelf(HomeShelf.Unread).titles())
    }

    /**
     * Before the unread pile, so the newest arrivals are shown with when they
     * came, and the pile holds the older ones.
     */
    @Test
    fun recentlyAddedHoldsTheNewestArrivals_andUnreadTheRest() {
        val rows = rowsOf(book("First"), book("Second"), book("Third"), limit = 1)

        assertEquals(listOf("Third"), rows[HomeShelf.RecentlyAdded])
        assertEquals(listOf("Second"), rows[HomeShelf.Unread])
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
        assertEquals(listOf("Book 2", "Book 1"), rows[HomeShelf.Unread])
    }

    /** A row's own screen holds all of it, including what Home shows elsewhere. */
    @Test
    fun aShelfHoldsEveryBookItSamples_evenThoseShownHigherOnHome() {
        val books = library(
            book("Kept And Current", favorite = true, progress = readAt(30)),
            book("Kept", favorite = true),
            book("Ordinary"),
        )

        assertEquals(listOf("Kept", "Kept And Current"), books.onShelf(HomeShelf.Favorites).titles())
        assertEquals(listOf("Ordinary", "Kept", "Kept And Current"), books.onShelf(HomeShelf.RecentlyAdded).titles())
    }
}
