package io.lukia.features.library.presentation

import io.lukia.MainDispatcherTest
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Narrowing and ordering the library, which the ViewModel does so the grid can
 * draw what it is handed.
 */
class LibraryFilteringTest : MainDispatcherTest() {

    @Test
    fun theGridIsGivenTheLibraryNarrowedAndOrdered() = runTest {
        val viewModel = createViewModel(
            FakeLibraryRepository(
                initialDocuments = listOf(
                    pdfDocumentInfo(location = "/test/solaris.pdf"),
                    pdfDocumentInfo(location = "/test/dune.pdf", favorite = true),
                ),
            ),
        )

        viewModel.updateQuery(LibraryQuery(favouritesOnly = true))

        assertEquals(listOf("/test/dune.pdf"), viewModel.books.value.map { it.location })
    }

    @Test
    fun theFiltersOfferedAreTheOnesThisLibraryCanAnswer() = runTest {
        val viewModel = createViewModel(
            FakeLibraryRepository(
                initialDocuments = listOf(
                    pdfDocumentInfo(location = "/test/solaris.pdf"),
                    pdfDocumentInfo(location = "/test/dune.pdf", favorite = true),
                ),
            ),
        )

        val facets = viewModel.facets.value

        // One format between them, so nothing to choose; one favourite of two,
        // so that is worth offering.
        assertEquals(emptyList(), facets.formats)
        assertTrue(facets.hasFavourites)
    }

    /**
     * Un-favouriting the last favourite takes the ground out from under whoever
     * was standing on it. The filter has to go with it, or the grid empties for
     * a reason nothing on screen explains.
     */
    @Test
    fun aFilterIsDroppedOnceTheLibraryCanNoLongerOfferIt() = runTest {
        val favourite = pdfDocumentInfo(location = "/test/dune.pdf", favorite = true)
        val viewModel = createViewModel(
            FakeLibraryRepository(
                initialDocuments = listOf(favourite, pdfDocumentInfo(location = "/test/solaris.pdf")),
            ),
        )
        viewModel.updateQuery(LibraryQuery(favouritesOnly = true))

        viewModel.toggleFavorite(favourite)

        assertFalse(viewModel.query.value.favouritesOnly)
        assertEquals(2, viewModel.books.value.size)
    }
}
