package io.lukia.features.library.presentation

import io.lukia.MainDispatcherTest
import io.lukia.core.domain.AppError
import io.lukia.core.domain.reading.FakeReadingProgressRepository
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.presentation.UiState
import io.lukia.features.library.presentation.model.LibraryMessage
import io.lukia.features.library.presentation.model.upkeep
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LibraryViewModelTest : MainDispatcherTest() {

    @Test
    fun loadsDocumentsOnInit() = runTest {
        val documentInfo = pdfDocumentInfo()
        val repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo))
        val viewModel = createViewModel(repository)

        assertEquals(UiState.Success(listOf(documentInfo)), viewModel.uiState.value)
    }

    /**
     * The shelf is read without its artwork, so what a card is handed is the way
     * to ask for one - see [LibraryBook.cover]. This is the whole path: the book
     * carries a handle, and the handle reads the cover the shelf left behind.
     */
    @Test
    fun aBookOnTheShelfCarriesTheWayToGetItsArtwork() = runTest {
        val artwork = byteArrayOf(4, 5, 6)
        val repository = FakeLibraryRepository(
            initialDocuments = listOf(pdfDocumentInfo(cover = artwork)),
        )
        val viewModel = createViewModel(repository)

        val book = viewModel.library.value.single()

        assertNull(book.document.coverImage)
        assertContentEquals(artwork, assertNotNull(book.cover).load())
    }

    @Test
    fun aLibraryReadThatFailsBecomesAFailureState() = runTest {
        val viewModel = createViewModel(ThrowingLibraryRepository())

        assertEquals(UiState.Failure(AppError.Unexpected()), viewModel.uiState.value)
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
        assertEquals(LibraryMessage.DetailsRefreshed, viewModel.message.value)
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

        assertEquals(LibraryMessage.Failed(AppError.Unexpected()), viewModel.message.value)
        assertEquals(UiState.Success(listOf(documentInfo)), viewModel.uiState.value)
    }

    /**
     * The end of the only path that can find out a book's file has gone. Nothing
     * looks on its own - a shelf of five thousand is five thousand trips to the
     * file system - so a re-reading is what corrects the row, and the shelf's
     * upkeep is what the reader is then offered.
     */
    @Test
    fun aRefreshThatFindsTheFileGoneLeavesTheBookMarkedMissing() = runTest {
        val documentInfo = pdfDocumentInfo()
        val repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo))
        val viewModel = createViewModel(
            repository = repository,
            analyzer = FakeDocumentAnalyzer(
                documentInfo = documentInfo,
                missingLocations = setOf(documentInfo.document.location),
            ),
        )

        viewModel.refreshDocument(documentInfo)

        assertEquals(
            LibraryMessage.Failed(AppError.DocumentNotFound),
            viewModel.message.value,
        )
        assertEquals(
            setOf(documentInfo.document.location),
            viewModel.books.value.upkeep().missing,
        )
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
