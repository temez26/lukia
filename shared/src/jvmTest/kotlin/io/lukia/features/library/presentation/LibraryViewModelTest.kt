package io.lukia.features.library.presentation

import io.lukia.MainDispatcherTest
import io.lukia.core.domain.AppError
import io.lukia.core.presentation.UiState
import io.lukia.features.reader.domain.FakeReadingProgressRepository
import io.lukia.features.reader.domain.ReadingProgress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryViewModelTest : MainDispatcherTest() {

    @Test
    fun loadsDocumentsOnInit() = runTest {
        val documentInfo = pdfDocumentInfo()
        val repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo))
        val viewModel = createViewModel(repository)

        assertEquals(UiState.Success(listOf(documentInfo)), viewModel.uiState.value)
    }

    @Test
    fun aLibraryReadThatFailsBecomesAFailureState() = runTest {
        val viewModel = createViewModel(ThrowingLibraryRepository())

        assertEquals(
            UiState.Failure(AppError.Unexpected("library read blew up")),
            viewModel.uiState.value,
        )
    }

    @Test
    fun toggleFavoriteMarksANonFavouriteDocument() = runTest {
        val documentInfo = pdfDocumentInfo()
        val repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo))
        val viewModel = createViewModel(repository)

        viewModel.toggleFavorite(documentInfo)

        assertEquals(listOf(documentInfo.copy(isFavorite = true)), viewModel.documents())
    }

    @Test
    fun toggleFavoriteClearsAFavouriteDocument() = runTest {
        val documentInfo = pdfDocumentInfo().copy(isFavorite = true)
        val repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo))
        val viewModel = createViewModel(repository)

        viewModel.toggleFavorite(documentInfo)

        assertEquals(listOf(documentInfo.copy(isFavorite = false)), viewModel.documents())
    }

    /**
     * The row is patched where it stands, and the notice is what tells the
     * reader anything happened - a re-reading that found the same thing changes
     * nothing on screen.
     */
    @Test
    fun refreshingABookStoresWhatTheSecondReadingFound() = runTest {
        val documentInfo = pdfDocumentInfo()
        val rescanned = documentInfo.copy(sizeBytes = 9999L)
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo)),
            analyzer = FakeDocumentAnalyzer(rescanned),
        )

        viewModel.refreshDocument(documentInfo)

        assertEquals(listOf(9999L), viewModel.documents().map { it.sizeBytes })
        assertEquals("Details refreshed", viewModel.message.value)
    }

    /** A file that can no longer be read must not take the library down with it. */
    @Test
    fun aRefreshThatFailsReportsItselfAndLeavesTheLibraryOnScreen() = runTest {
        val documentInfo = pdfDocumentInfo()
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo)),
            analyzer = ThrowingDocumentAnalyzer(),
        )

        viewModel.refreshDocument(documentInfo)

        assertEquals("analysis blew up", viewModel.message.value)
        assertEquals(UiState.Success(listOf(documentInfo)), viewModel.uiState.value)
    }

    @Test
    fun deleteDocumentRemovesItFromTheLibrary() = runTest {
        val documentInfo = pdfDocumentInfo()
        val repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo))
        val viewModel = createViewModel(repository)

        viewModel.deleteDocument(documentInfo)

        assertEquals(emptyList(), viewModel.documents())
    }

    /** The grid draws a bar on each cover, so it needs every position at once. */
    @Test
    fun loadsWhereEachBookWasLeftOff() = runTest {
        val repository = FakeLibraryRepository(listOf(pdfDocumentInfo()))
        val positions = FakeReadingProgressRepository(
            mutableMapOf("/test/book.pdf" to ReadingProgress(pageIndex = 41, pageCount = 100)),
        )

        val viewModel = createViewModel(repository, readingProgress = positions)

        assertEquals(
            ReadingProgress(pageIndex = 41, pageCount = 100),
            viewModel.readingProgress.value["/test/book.pdf"],
        )
    }

    /**
     * The books are what the screen is for and the bars are a decoration on
     * top, so a position store that cannot be read must not take the library
     * off the screen with it.
     */
    @Test
    fun aPositionStoreThatFailsStillLeavesTheLibraryOnScreen() = runTest {
        val repository = FakeLibraryRepository(listOf(pdfDocumentInfo()))

        val viewModel = createViewModel(repository, readingProgress = ThrowingProgressRepository())

        assertEquals(1, viewModel.documents().size)
        assertEquals(emptyMap(), viewModel.readingProgress.value)
    }
}
