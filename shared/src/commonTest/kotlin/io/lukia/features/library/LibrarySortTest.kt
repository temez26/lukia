package io.lukia.features.library

import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.features.library.presentation.model.LibrarySort
import io.lukia.features.library.presentation.model.sortedFor
import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySortTest {

    private val shelf = library(
        book("Solaris", authors = listOf("Stanisław Lem"), sizeBytes = 3_000L),
        book("Dune", authors = listOf("Frank Herbert"), sizeBytes = 9_000L),
        book("Notes", sizeBytes = 100L),
    )

    /** The order the books were imported in, which is the one the reader built. */
    @Test
    fun theDefaultIsTheShelfsOwnOrder() {
        assertEquals(
            listOf("Solaris", "Dune", "Notes"),
            shelf.sortedFor(LibrarySort.Added).titles(),
        )
        assertEquals(
            listOf("Notes", "Dune", "Solaris"),
            shelf.sortedFor(LibrarySort.RecentlyAdded).titles(),
        )
    }

    @Test
    fun titlesSortBothWays() {
        assertEquals(
            listOf("Dune", "Notes", "Solaris"),
            shelf.sortedFor(LibrarySort.Title).titles(),
        )
        assertEquals(
            listOf("Solaris", "Notes", "Dune"),
            shelf.sortedFor(LibrarySort.TitleReversed).titles(),
        )
    }

    /** A file that credits nobody cannot answer the question, so it goes last. */
    @Test
    fun booksWithNoAuthorSortToTheEnd() {
        assertEquals(
            listOf("Dune", "Solaris", "Notes"),
            shelf.sortedFor(LibrarySort.Author).titles(),
        )
    }

    @Test
    fun sizeSortsLargestFirst() {
        assertEquals(
            listOf("Dune", "Solaris", "Notes"),
            shelf.sortedFor(LibrarySort.Largest).titles(),
        )
    }

    /**
     * `readAt` counts writes rather than telling the time - see
     * `ReadingPosition.sq` - so the highest is simply the newest.
     */
    @Test
    fun recentlyReadPutsTheLastBookOpenedFirstAndUnreadBooksLast() {
        val books = library(
            book("Solaris", progress = ReadingProgress(1, 100, readAt = 4)),
            book("Dune", progress = ReadingProgress(1, 100, readAt = 9)),
            book("Notes"),
        )

        assertEquals(
            listOf("Dune", "Solaris", "Notes"),
            books.sortedFor(LibrarySort.LastRead).titles(),
        )
    }

    /**
     * A position stored before the app recorded this carries no count at all.
     * It still ranks above a book nobody has ever opened, which is the most that
     * can honestly be said about it.
     */
    @Test
    fun aBookReadBeforeCountingBeganStillOutranksAnUnreadOne() {
        val books = library(
            book("Notes"),
            book("Dune", progress = ReadingProgress(1, 100, readAt = 0)),
        )

        assertEquals(listOf("Dune", "Notes"), books.sortedFor(LibrarySort.LastRead).titles())
    }

    /** Ties keep the shelf's order rather than swapping between passes. */
    @Test
    fun sortingIsStable() {
        val books = library(
            book("Zeta", authors = listOf("Same Author")),
            book("Alpha", authors = listOf("Same Author")),
        )

        assertEquals(
            listOf("Alpha", "Zeta"),
            books.sortedFor(LibrarySort.Author).titles(),
        )
    }
}
