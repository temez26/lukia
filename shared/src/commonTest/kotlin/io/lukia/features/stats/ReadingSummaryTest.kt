package io.lukia.features.stats

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.features.library.TestBook
import io.lukia.features.library.book
import io.lukia.features.library.finished
import io.lukia.features.library.partRead
import io.lukia.features.stats.domain.ReadingSummary
import io.lukia.features.stats.domain.readingSummary
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

    private fun summaryOf(vararg books: TestBook): ReadingSummary = readingSummary(
        documents = books.map(TestBook::document),
        progress = books.mapNotNull { entry ->
            entry.progress?.let { entry.document.document.location to it }
        }.toMap(),
    )

    private fun readAt(stamp: Long) =
        ReadingProgress(pageIndex = 20, pageCount = 100, readAt = stamp)

    @Test
    fun booksAreSplitIntoBeingRead_finishedAndNeverOpened() {
        val stats = summaryOf(
            book("Halfway", progress = partRead()),
            book("Done", progress = finished()),
            book("Waiting"),
        ).stats

        assertEquals(3, stats.booksInLibrary)
        assertEquals(1, stats.booksReading)
        assertEquals(1, stats.booksFinished)
        assertEquals(1, stats.booksUnread)
    }

    /** The page the reader is on, counted from one, added up over what they opened. */
    @Test
    fun pagesReadAddUpTheFurthestPageOfEveryOpenedBook() {
        val stats = summaryOf(
            book("Halfway", progress = ReadingProgress(pageIndex = 20, pageCount = 100)),
            book("Done", progress = ReadingProgress(pageIndex = 9, pageCount = 10)),
            book("Waiting"),
        ).stats

        assertEquals(21 + 10, stats.pagesRead)
    }

    @Test
    fun favouritesAreCountedWhetherOrNotTheyHaveBeenOpened() {
        val stats = summaryOf(
            book("Kept", favorite = true),
            book("Kept and read", favorite = true, progress = partRead()),
            book("Ordinary"),
        ).stats

        assertEquals(2, stats.favourites)
    }

    /**
     * Averaged over the library rather than over what has been opened, so the
     * figure answers "how much of what I own have I read".
     */
    @Test
    fun libraryProgressCountsUnopenedBooksAsNoneOfThem() {
        val fraction = summaryOf(
            book("Done", progress = finished()),
            book("Waiting"),
        ).stats.libraryFraction

        assertEquals(0.5f, fraction)
    }

    @Test
    fun anEmptyLibraryHasNoProgressToReport() {
        val stats = summaryOf().stats

        assertNull(stats.libraryFraction)
        assertTrue(stats.isEmpty)
    }

    @Test
    fun recentActivityIsOrderedByWhatWasReadLast() {
        val recent = summaryOf(
            book("Older", progress = readAt(10)),
            book("Newest", progress = readAt(30)),
            book("Middle", progress = readAt(20)),
        ).recent

        assertEquals(listOf("Newest", "Middle", "Older"), recent.map { it.document.title() })
    }

    @Test
    fun booksThatWereNeverOpenedAreNotActivity() {
        val recent = summaryOf(
            book("Read", progress = partRead()),
            book("Waiting"),
        ).recent

        assertEquals(listOf("Read"), recent.map { it.document.title() })
    }

    @Test
    fun recentActivityIsCappedAtTheLimitItIsGiven() {
        val books = (1..8).map { book("Book $it", progress = readAt(it.toLong())) }

        val recent = readingSummary(
            documents = books.map(TestBook::document),
            progress = books.associate { it.document.document.location to it.progress!! },
            recentLimit = 3,
        ).recent

        assertEquals(3, recent.size)
    }

    /**
     * A position can outlive the book it belongs to - the store keys on location
     * and takes no foreign key - and a book removed from the library must stop
     * counting towards its owner's reading.
     */
    @Test
    fun positionsWithNoBookInTheLibraryAreIgnored() {
        val summary = readingSummary(
            documents = listOf(book("Kept", progress = partRead()).document),
            progress = mapOf(
                "/library/Kept.pdf" to partRead(),
                "/library/Removed.pdf" to finished(),
            ),
        )

        assertEquals(1, summary.stats.booksInLibrary)
        assertEquals(0, summary.stats.booksFinished)
        assertEquals(listOf("Kept"), summary.recent.map { it.document.title() })
    }

    private fun DocumentInfo.title(): String = document.fileName.substringBeforeLast('.')
}
