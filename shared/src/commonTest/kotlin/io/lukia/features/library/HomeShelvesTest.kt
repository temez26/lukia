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

    /**
     * A heading over a blank strip teaches nothing, and three of them on a fresh
     * library is a screen that looks broken rather than new.
     */
    @Test
    fun aRowWithNothingOnItIsLeftOutEntirely() {
        val rows = rowsOf(book("Unread And Unloved"))

        assertEquals(setOf(HomeShelf.RecentlyAdded), rows.keys)
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
