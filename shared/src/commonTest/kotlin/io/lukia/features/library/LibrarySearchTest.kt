package io.lukia.features.library

import io.lukia.features.library.presentation.LibraryQuery
import io.lukia.features.library.presentation.matching
import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySearchTest {

    private val shelf = library(
        book("Moby Dick"),
        book("The Hobbit"),
        book("dune", authors = listOf("Frank Herbert"), declaredTitle = "Dune: A Novel")
    )

    private fun found(query: String) = shelf.matching(LibraryQuery(search = query)).titles()

    @Test
    fun blankQueryMatchesEverything() {
        assertEquals(listOf("Moby Dick", "The Hobbit", "dune"), found(""))
        assertEquals(listOf("Moby Dick", "The Hobbit", "dune"), found("   "))
    }

    @Test
    fun matchesOnAnyPartOfTheTitle() {
        assertEquals(listOf("The Hobbit"), found("hobb"))
    }

    @Test
    fun matchingIgnoresCase() {
        assertEquals(listOf("dune"), found("DUNE"))
        assertEquals(listOf("Moby Dick"), found("moby"))
    }

    /** Trailing spaces come free with typing, and should not empty the library. */
    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertEquals(listOf("dune"), found("  dune  "))
    }

    /**
     * The extension is stripped for display, so it is not part of the title a
     * reader can see - and so not something they can search for.
     */
    @Test
    fun extensionIsNotSearchable() {
        assertEquals(emptyList(), found("pdf"))
    }

    /**
     * A reader looking for a book by its author is looking for the book. The
     * title the document declares is searchable for the same reason: it is what
     * the book calls itself, even where the file was saved under something else.
     */
    @Test
    fun authorsAndTheDeclaredTitleAreSearchableToo() {
        assertEquals(listOf("dune"), found("herbert"))
        assertEquals(listOf("dune"), found("a novel"))
    }

    @Test
    fun matchesKeepTheShelfsOrder() {
        assertEquals(listOf("Moby Dick", "The Hobbit", "dune"), found(""))
    }
}
