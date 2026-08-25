package io.lukia.features.library

import io.lukia.features.library.presentation.HomeShelf
import io.lukia.features.library.presentation.homeRows
import io.lukia.features.reader.domain.ReadingProgress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** How the mobile home screen groups a library into the three rows it shows. */
class HomeShelvesTest {

    private fun readAt(stamp: Long) =
        ReadingProgress(pageIndex = 20, pageCount = 100, readAt = stamp)

    private fun rowsOf(vararg books: TestBook, limit: Int = 12) =
        library(*books).homeRows(limit).associate { it.shelf to it.books.titles() }

    @Test
    fun recentHoldsWhatHasBeenOpened_mostRecentlyReadFirst() {
        val rows = rowsOf(
            book("Older", progress = readAt(10)),
            book("Never Opened"),
            book("Newer", progress = readAt(20)),
        )

        assertEquals(listOf("Newer", "Older"), rows[HomeShelf.Recent])
    }

    /**
     * "Recent" is a record of what a reader has been doing, so a book they got
     * to the end of last night belongs on it as much as one they are halfway
     * through.
     */
    @Test
    fun recentKeepsBooksThatHaveBeenFinished() {
        val rows = rowsOf(
            book("Halfway", progress = readAt(10)),
            book("Done", progress = ReadingProgress(pageIndex = 99, pageCount = 100, readAt = 20)),
        )

        assertEquals(listOf("Done", "Halfway"), rows[HomeShelf.Recent])
    }

    @Test
    fun recentlyAddedIsTheImportOrderReversed() {
        val rows = rowsOf(book("First"), book("Second"), book("Third"))

        assertEquals(listOf("Third", "Second", "First"), rows[HomeShelf.RecentlyAdded])
    }

    @Test
    fun favoritesHoldsOnlyWhatWasMarked() {
        val rows = rowsOf(
            book("Kept", favorite = true),
            book("Ordinary"),
            book("Also Kept", favorite = true),
        )

        assertEquals(listOf("Also Kept", "Kept"), rows[HomeShelf.Favorites])
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

    @Test
    fun finishedHoldsWhatWasReadToTheEnd_mostRecentlyFinishedFirst() {
        val rows = rowsOf(
            book("Long Ago", progress = ReadingProgress(99, 100, readAt = 10)),
            book("Halfway", progress = readAt(30)),
            book("Last Night", progress = ReadingProgress(99, 100, readAt = 20)),
        )

        assertEquals(listOf("Last Night", "Long Ago"), rows[HomeShelf.Finished])
    }

    /**
     * The row exists to answer "what can I finish", so the answer is the top of
     * it - and a book already finished has a row of its own.
     */
    @Test
    fun almostDoneIsClosestToTheEndFirst_andExcludesWhatIsAlreadyDone() {
        val rows = rowsOf(
            book("Three Quarters", progress = ReadingProgress(74, 100)),
            book("Barely Started", progress = ReadingProgress(9, 100)),
            book("Nearly There", progress = ReadingProgress(94, 100)),
            book("Done", progress = finished()),
        )

        assertEquals(listOf("Nearly There", "Three Quarters"), rows[HomeShelf.AlmostDone])
    }

    /**
     * A heading over a blank strip teaches nothing, and six of them on a fresh
     * library is a screen that looks broken rather than new.
     */
    @Test
    fun aRowWithNothingOnItIsLeftOutEntirely() {
        val rows = rowsOf(book("Unread And Unloved"))

        assertEquals(setOf(HomeShelf.RecentlyAdded, HomeShelf.Unread), rows.keys)
    }

    @Test
    fun anEmptyLibraryHasNoRowsAtAll() {
        assertTrue(library().homeRows().isEmpty())
    }

    /** Past a rowful, the way to the rest is "See All" rather than a longer flick. */
    @Test
    fun eachRowStopsAtItsLimit() {
        val books = (1..5).map { book("Book $it") }.toTypedArray()

        val rows = rowsOf(*books, limit = 3)

        assertEquals(listOf("Book 5", "Book 4", "Book 3"), rows[HomeShelf.RecentlyAdded])
    }
}
