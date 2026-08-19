package io.lukia.features.library

import io.lukia.core.domain.DocumentType
import io.lukia.features.library.presentation.LibraryFacets
import io.lukia.features.library.presentation.LibraryQuery
import io.lukia.features.library.presentation.LibraryShelf
import io.lukia.features.library.presentation.facets
import io.lukia.features.library.presentation.matching
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryQueryTest {

    private val shelf = library(
        book("Dune", type = DocumentType.PDF, favorite = true, progress = partRead()),
        book("Solaris", type = DocumentType.EPUB, authors = listOf("Stanisław Lem")),
        book("Notes", type = DocumentType.DOCX, progress = finished()),
    )

    @Test
    fun anEmptyQueryIsTheWholeLibraryInTheOrderItArrived() {
        assertEquals(listOf("Dune", "Solaris", "Notes"), shelf.matching(LibraryQuery()).titles())
    }

    @Test
    fun aShelfIsWhereEachBookStandsWithItsReader() {
        assertEquals(
            listOf("Dune"),
            shelf.matching(LibraryQuery(shelf = LibraryShelf.Reading)).titles(),
        )
        assertEquals(
            listOf("Solaris"),
            shelf.matching(LibraryQuery(shelf = LibraryShelf.Unread)).titles(),
        )
        assertEquals(
            listOf("Notes"),
            shelf.matching(LibraryQuery(shelf = LibraryShelf.Finished)).titles(),
        )
    }

    @Test
    fun favouritesAndFormatNarrowIndependently() {
        assertEquals(
            listOf("Dune"),
            shelf.matching(LibraryQuery(favouritesOnly = true)).titles(),
        )
        assertEquals(
            listOf("Solaris"),
            shelf.matching(LibraryQuery(format = DocumentType.EPUB)).titles(),
        )
        // Both at once is both, which is nothing here: the favourite is a PDF.
        assertEquals(
            emptyList(),
            shelf.matching(
                LibraryQuery(favouritesOnly = true, format = DocumentType.EPUB),
            ).titles(),
        )
    }

    /**
     * One book files itself under `Fiction` and the next under `fiction`. They
     * are the same shelf, and a filter that disagreed would put half the books
     * behind each.
     */
    @Test
    fun aCategoryMatchesWhateverCaseTheDocumentWroteItIn() {
        val books = library(
            book("Dune", subjects = listOf("Science Fiction")),
            book("Solaris", subjects = listOf("science fiction")),
            book("Notes", subjects = listOf("Reference")),
        )

        assertEquals(
            listOf("Dune", "Solaris"),
            books.matching(LibraryQuery(category = "Science Fiction")).titles(),
        )
    }

    @Test
    fun anAuthorNarrowsToWhatTheyWrote() {
        val books = library(
            book("Solaris", authors = listOf("Stanisław Lem")),
            book("The Invincible", authors = listOf("Stanisław Lem")),
            book("Dune", authors = listOf("Frank Herbert")),
        )

        assertEquals(
            listOf("Solaris", "The Invincible"),
            books.matching(LibraryQuery(author = "Stanisław Lem")).titles(),
        )
    }

    /** A region is a distinction for a spell checker, not for a reader. */
    @Test
    fun aLanguageIgnoresTheRegionTheTagCarries() {
        val books = library(
            book("Dune", language = "en-GB"),
            book("Solaris", language = "en"),
            book("Seitsemän veljestä", language = "fi"),
        )

        assertEquals(
            listOf("Dune", "Solaris"),
            books.matching(LibraryQuery(language = "en")).titles(),
        )
    }

    @Test
    fun aSearchAndAFilterBothApply() {
        val books = library(
            book("Dune", favorite = true),
            book("Dune Messiah"),
            book("Solaris", favorite = true),
        )

        assertEquals(
            listOf("Dune"),
            books.matching(LibraryQuery(search = "dune", favouritesOnly = true)).titles(),
        )
    }

    @Test
    fun clearingLeavesTheShelfAndTheSearchAlone() {
        val query = LibraryQuery(
            shelf = LibraryShelf.Reading,
            search = "dune",
            favouritesOnly = true,
            format = DocumentType.PDF,
            category = "Fiction",
        )

        val cleared = query.cleared()

        assertFalse(cleared.isRefined)
        assertEquals(LibraryShelf.Reading, cleared.shelf)
        assertEquals("dune", cleared.search)
    }

    /**
     * The import slot at the end of the grid hangs on this, and a book added to
     * a narrowed view lands in the library but not on the screen that took it.
     */
    @Test
    fun anythingAtAllNarrowingTheLibraryCountsAsNarrowed() {
        assertFalse(LibraryQuery().isNarrowed)
        assertTrue(LibraryQuery(search = "dune").isNarrowed)
        assertTrue(LibraryQuery(shelf = LibraryShelf.Unread).isNarrowed)
        assertTrue(LibraryQuery(favouritesOnly = true).isNarrowed)
        // An order is not a narrowing: every book is still on screen.
        assertFalse(LibraryQuery(sort = io.lukia.features.library.presentation.LibrarySort.Title).isNarrowed)
    }

    /**
     * Un-favouriting the last favourite takes the ground out from under whoever
     * was standing on it, and a filter nothing can match empties the grid for a
     * reason nothing on screen explains.
     */
    @Test
    fun aFilterTheLibraryCanNoLongerOfferIsDropped() {
        val query = LibraryQuery(
            shelf = LibraryShelf.Finished,
            favouritesOnly = true,
            format = DocumentType.EPUB,
            category = "Fiction",
            author = "Frank Herbert",
            language = "fi",
            search = "dune",
        )

        val limited = query.limitedTo(LibraryFacets.Empty)

        assertEquals(LibraryShelf.All, limited.shelf)
        assertFalse(limited.isRefined)
        // What the reader typed is theirs, and no facet can take it away.
        assertEquals("dune", limited.search)
    }

    @Test
    fun aFilterTheLibraryStillOffersSurvives() {
        val query = LibraryQuery(format = DocumentType.EPUB, shelf = LibraryShelf.Unread)

        assertEquals(query, query.limitedTo(shelf.facets()))
    }
}
