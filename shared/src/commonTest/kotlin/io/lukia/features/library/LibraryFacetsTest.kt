package io.lukia.features.library

import io.lukia.core.domain.document.DocumentType
import io.lukia.features.library.presentation.model.LibraryFacets
import io.lukia.features.library.presentation.model.LibraryShelf
import io.lukia.features.library.presentation.model.facets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A filter is only worth offering when it divides the library: one that matches
 * nothing is a dead end, and one that matches everything is the unfiltered
 * library under another name.
 */
class LibraryFacetsTest {

    @Test
    fun anEmptyLibraryOffersNothing() {
        assertEquals(LibraryFacets.Empty, library().facets())
    }

    @Test
    fun aLibraryOfOneFormatOffersNoFormatFilter() {
        val books = library(
            book("Dune", type = DocumentType.EPUB),
            book("Solaris", type = DocumentType.EPUB),
        )

        assertEquals(emptyList(), books.facets().formats)
    }

    @Test
    fun everyFormatPresentEarnsARowWhenMoreThanOneIs() {
        val books = library(
            book("Dune", type = DocumentType.PDF),
            book("Solaris", type = DocumentType.EPUB),
            book("Notes", type = DocumentType.DOCX),
        )

        assertEquals(
            listOf(DocumentType.PDF, DocumentType.EPUB, DocumentType.DOCX),
            books.facets().formats,
        )
    }

    @Test
    fun favouritesAreOfferedOnlyWhenSomeButNotAllBooksAreOne() {
        assertFalse(library(book("Dune")).facets().hasFavourites)
        assertFalse(library(book("Dune", favorite = true)).facets().hasFavourites)
        assertTrue(
            library(book("Dune", favorite = true), book("Solaris")).facets().hasFavourites,
        )
    }

    @Test
    fun booksOnlyOnThisDeviceAreOfferedOnlyAlongsideBooksOnAServer() {
        // Nobody signed in: no book is on a server, and nothing about one is offered.
        assertFalse(library(book("Dune"), book("Solaris")).facets().hasDeviceOnly)
        assertTrue(library(book("Dune", onServer = true), book("Solaris")).facets().hasDeviceOnly)
        assertFalse(library(book("Dune", onServer = true), book("Solaris", onServer = true)).facets().hasDeviceOnly)
    }

    @Test
    fun theShelvesAreTheOnesWithBooksOnThem() {
        val books = library(
            book("Dune", progress = partRead()),
            book("Solaris"),
        )

        assertEquals(
            listOf(LibraryShelf.All, LibraryShelf.Reading, LibraryShelf.Unread),
            books.facets().shelves,
        )
    }

    /** A shelf holding the whole library is the All shelf under another name. */
    @Test
    fun aLibraryNobodyHasOpenedOffersNoShelvesAtAll() {
        val books = library(book("Dune"), book("Solaris"))

        assertEquals(listOf(LibraryShelf.All), books.facets().shelves)
    }

    @Test
    fun categoriesAreAlphabeticalAndFiledOnceWhateverTheirCase() {
        val books = library(
            book("Dune", subjects = listOf("Science Fiction")),
            book("Solaris", subjects = listOf("science fiction")),
            book("Notes", subjects = listOf("Reference")),
        )

        assertEquals(listOf("Reference", "Science Fiction"), books.facets().categories)
    }

    /** Every book under one category is not a division of anything. */
    @Test
    fun aCategoryEveryBookCarriesIsNotOffered() {
        val books = library(
            book("Dune", subjects = listOf("Fiction")),
            book("Solaris", subjects = listOf("Fiction")),
        )

        assertEquals(emptyList(), books.facets().categories)
    }

    /**
     * What a filter matches is whether a book carries a value, not how many
     * times it says so - so a book that files itself under one category twice
     * is still one book carrying it, and cannot on its own make that category
     * look like a division of the library.
     */
    @Test
    fun aCategoryRepeatedInsideOneBookIsStillOneBookCarryingIt() {
        val books = library(
            book("Dune", subjects = listOf("Fiction", "fiction")),
            book("Solaris", subjects = listOf("Fiction")),
        )

        assertEquals(emptyList(), books.facets().categories)
    }

    @Test
    fun authorsAndLanguagesFollowTheSameRule() {
        val books = library(
            book("Solaris", authors = listOf("Stanisław Lem"), language = "pl"),
            book("Dune", authors = listOf("Frank Herbert"), language = "en-US"),
        )

        val facets = books.facets()

        assertEquals(listOf("Frank Herbert", "Stanisław Lem"), facets.authors)
        assertEquals(listOf("en", "pl"), facets.languages)
    }
}
