package io.lukia.features.stats

import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.features.library.TestBook
import io.lukia.features.library.book
import io.lukia.features.library.finished
import io.lukia.features.library.partRead
import io.lukia.features.stats.domain.ReadingStats
import io.lukia.features.stats.domain.readingStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the statistics say about a reader's reading, worked out from the library
 * and the positions stored against it.
 *
 * Built from the same `book` helper the library's own tests use: what is counted
 * here is what that store actually holds, and a second set of fixtures would be
 * a second idea of what a book is.
 */
class ReadingSummaryTest {

    private fun statsOf(vararg books: TestBook): ReadingStats = readingStats(
        documents = books.map(TestBook::document),
        progress = books.mapNotNull { entry ->
            entry.progress?.let { entry.document.document.location to it }
        }.toMap(),
    )

    @Test
    fun booksAreSplitIntoBeingRead_finishedAndNeverOpened() {
        val stats = statsOf(
            book("Halfway", progress = partRead()),
            book("Done", progress = finished()),
            book("Waiting"),
        )

        assertEquals(3, stats.booksInLibrary)
        assertEquals(1, stats.booksReading)
        assertEquals(1, stats.booksFinished)
        assertEquals(1, stats.booksUnread)
    }

    /** The page the reader is on, counted from one, added up over what they opened. */
    @Test
    fun pagesReadAddUpTheFurthestPageOfEveryOpenedBook() {
        val stats = statsOf(
            book("Halfway", progress = ReadingProgress(pageIndex = 20, pageCount = 100)),
            book("Done", progress = ReadingProgress(pageIndex = 9, pageCount = 10)),
            book("Waiting"),
        )

        assertEquals(21 + 10, stats.pagesRead)
    }

    @Test
    fun favouritesAreCountedWhetherOrNotTheyHaveBeenOpened() {
        val stats = statsOf(
            book("Kept", favorite = true),
            book("Kept and read", favorite = true, progress = partRead()),
            book("Ordinary"),
        )

        assertEquals(2, stats.favourites)
    }

    /**
     * Averaged over the library rather than over what has been opened, so the
     * figure answers "how much of what I own have I read".
     */
    @Test
    fun libraryProgressCountsUnopenedBooksAsNoneOfThem() {
        val fraction = statsOf(
            book("Done", progress = finished()),
            book("Waiting"),
        ).libraryFraction

        assertEquals(0.5f, fraction)
    }

    @Test
    fun anEmptyLibraryHasNoProgressToReport() {
        val stats = statsOf()

        assertNull(stats.libraryFraction)
        assertTrue(stats.isEmpty)
    }

    /**
     * A position can outlive the book it belongs to - the store keys on location
     * and takes no foreign key - and a book removed from the library must stop
     * counting towards its owner's reading.
     */
    @Test
    fun positionsWithNoBookInTheLibraryAreIgnored() {
        val stats = readingStats(
            documents = listOf(book("Kept", progress = partRead()).document),
            progress = mapOf(
                "/library/Kept.pdf" to partRead(),
                "/library/Removed.pdf" to finished(),
            ),
        )

        assertEquals(1, stats.booksInLibrary)
        assertEquals(0, stats.booksFinished)
    }
}
