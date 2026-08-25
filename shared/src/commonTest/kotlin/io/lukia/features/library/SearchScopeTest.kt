package io.lukia.features.library

import io.lukia.core.domain.DocumentType
import io.lukia.features.library.presentation.searching
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the search screen looks in, as opposed to what a search field on the
 * shelf would. The matching itself is [LibrarySearchTest]'s subject; this is
 * about the scope the search tab searches over.
 */
class SearchScopeTest {

    private val shelf = library(
        book("Moby Dick", favorite = true),
        book("The Hobbit", type = DocumentType.EPUB, progress = finished()),
        book("Dune", authors = listOf("Frank Herbert")),
    )

    private fun found(term: String) = shelf.searching(term).titles()

    /**
     * The prompt is the answer before anything has been typed. Handing back the
     * whole library instead would answer a question nobody has asked.
     */
    @Test
    fun nothingIsFoundUntilSomethingIsTyped() {
        assertEquals(emptyList(), found(""))
        assertEquals(emptyList(), found("   "))
    }

    @Test
    fun searchingFindsBooksByTitleAndByAuthor() {
        assertEquals(listOf("The Hobbit"), found("hobbit"))
        assertEquals(listOf("Dune"), found("herbert"))
    }

    /**
     * A reader searching is searching all of their books. Every property the
     * shelf can be narrowed by - the format, the favourite, how far through it
     * is - is deliberately not applied here.
     */
    @Test
    fun searchingLooksAtEveryBookWhateverTheShelfIsNarrowedTo() {
        // In the library's own order: a favourite, a finished EPUB, and a book
        // matched on its author alone.
        assertEquals(listOf("Moby Dick", "The Hobbit", "Dune"), found("b"))
    }
}
