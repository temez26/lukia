package io.lukia.features.library.presentation

import io.lukia.MainDispatcherTest
import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.core.domain.document.CoverImage
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.FakeIncomingDocumentStore
import io.lukia.core.domain.document.ServerBookDownloader
import io.lukia.core.domain.document.ShelfChanges
import io.lukia.core.domain.document.TestDocuments
import io.lukia.core.domain.reading.FakePdfLayoutRepository
import io.lukia.core.domain.reading.FakeReadingProgressRepository
import io.lukia.core.domain.storage.FakeDocumentWorkspace
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.library.TypeFromExtensionAnalyzer
import io.lukia.features.library.application.BookDetails
import io.lukia.features.library.application.GetDocumentDetailsUseCase
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.features.library.application.RefreshDocumentUseCase
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.library.application.SetFavoriteUseCase
import io.lukia.features.library.domain.DocumentAnalyzer
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.library.presentation.model.LibraryMessage
import io.lukia.features.library.presentation.screens.BookDownload
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BookDetailViewModelTest : MainDispatcherTest() {

    @Test
    fun loadsMatchingDocumentOnInit() = runTest {
        val documentInfo = pdfDocumentInfo()
        val repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo))
        val viewModel = createViewModel(location = "/test/book.pdf", repository = repository)

        assertEquals(UiState.Success(BookDetails(documentInfo)), viewModel.uiState.value)
    }

    /**
     * A location with nothing behind it is a failure, not empty content: this
     * screen exists to show one book.
     */
    @Test
    fun aLocationWithNoMatchBecomesDocumentNotFound() = runTest {
        val viewModel = createViewModel(
            location = "/test/missing.pdf",
            repository = FakeLibraryRepository(),
        )

        assertEquals(UiState.Failure(AppError.DocumentNotFound), viewModel.uiState.value)
    }

    @Test
    fun aReadThatThrowsBecomesAnUnexpectedFailure() = runTest {
        val viewModel = createViewModel(
            location = "/test/book.pdf",
            repository = ThrowingLibraryRepository(),
        )

        assertEquals(UiState.Failure(AppError.Unexpected()), viewModel.uiState.value)
    }

    @Test
    fun aShelfChangeFromASyncShowsTheBookAfresh() = runTest {
        val repository = FakeLibraryRepository()
        val shelfChanges = ShelfChanges()
        val viewModel = createViewModel(
            location = "/test/book.pdf",
            repository = repository,
            shelfChanges = shelfChanges,
        )

        repository.addDocument(pdfDocumentInfo())
        shelfChanges.announce()

        assertEquals(UiState.Success(BookDetails(pdfDocumentInfo())), viewModel.uiState.value)
    }

    @Test
    fun comingBackToTheBookReadsItAgain() = runTest {
        val repository = FakeLibraryRepository(initialDocuments = listOf(pdfDocumentInfo()))
        val viewModel = createViewModel(location = "/test/book.pdf", repository = repository)

        repository.setFavorite("/test/book.pdf", true)
        viewModel.reload()

        assertEquals(
            UiState.Success(BookDetails(pdfDocumentInfo().copy(isFavorite = true))),
            viewModel.uiState.value,
        )
    }

    @Test
    fun retryReloadsTheDocument() = runTest {
        val documentInfo = pdfDocumentInfo()
        val repository = FakeLibraryRepository()
        val viewModel = createViewModel(location = "/test/book.pdf", repository = repository)

        assertEquals(UiState.Failure(AppError.DocumentNotFound), viewModel.uiState.value)

        repository.addDocument(documentInfo)
        viewModel.retry()

        assertEquals(UiState.Success(BookDetails(documentInfo)), viewModel.uiState.value)
    }

    @Test
    fun toggleFavoriteMarksANonFavouriteDocument() = runTest {
        val repository = FakeLibraryRepository(initialDocuments = listOf(pdfDocumentInfo()))
        val viewModel = createViewModel(location = "/test/book.pdf", repository = repository)

        viewModel.toggleFavorite()

        assertEquals(
            UiState.Success(BookDetails(pdfDocumentInfo().copy(isFavorite = true))),
            viewModel.uiState.value,
        )
    }

    @Test
    fun toggleFavoriteClearsAFavouriteDocument() = runTest {
        val documentInfo = pdfDocumentInfo().copy(isFavorite = true)
        val repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo))
        val viewModel = createViewModel(location = "/test/book.pdf", repository = repository)

        viewModel.toggleFavorite()

        assertEquals(
            UiState.Success(BookDetails(documentInfo.copy(isFavorite = false))),
            viewModel.uiState.value,
        )
    }

    @Test
    fun toggleFavoriteOnAFailedStateDoesNothing() = runTest {
        val repository = FakeLibraryRepository()
        val viewModel = createViewModel(location = "/test/missing.pdf", repository = repository)

        viewModel.toggleFavorite()

        assertEquals(UiState.Failure(AppError.DocumentNotFound), viewModel.uiState.value)
    }

    @Test
    fun deleteDocumentRemovesItFromTheLibrary() = runTest {
        val repository = FakeLibraryRepository(initialDocuments = listOf(pdfDocumentInfo()))
        val viewModel = createViewModel(location = "/test/book.pdf", repository = repository)

        viewModel.deleteDocument()

        assertEquals(emptyList(), repository.getDocuments())
    }

    /** This screen shows one book, so a refresh reloads it rather than patching it. */
    @Test
    fun refreshingReloadsTheBookAndSaysSo() = runTest {
        val documentInfo = pdfDocumentInfo()
        val repository = FakeLibraryRepository(listOf(documentInfo.copy(sizeBytes = 1L)))
        val viewModel = createViewModel("/test/book.pdf", repository)

        viewModel.refreshDocument()

        assertEquals(
            1024L,
            (viewModel.uiState.value as UiState.Success).data.document.sizeBytes,
        )
        assertEquals(LibraryMessage.DetailsRefreshed, viewModel.message.value)
    }

    /** A file that can no longer be read must not take the book off the screen. */
    @Test
    fun aRefreshThatFailsLeavesTheBookOnScreen() = runTest {
        val documentInfo = pdfDocumentInfo()
        val repository = FakeLibraryRepository(listOf(documentInfo))
        val viewModel = createViewModel(
            location = "/test/book.pdf",
            repository = repository,
            analyzer = TypeFromExtensionAnalyzer(
                failingLocations = setOf("/test/book.pdf"),
            ),
        )

        viewModel.refreshDocument()

        assertEquals(
            documentInfo,
            (viewModel.uiState.value as UiState.Success).data.document,
        )
        // The reader is told, and not with the analyzer's own words - which name
        // the file, and on a desktop name the person whose folder it sits in.
        assertEquals(LibraryMessage.Failed(AppError.Unexpected()), viewModel.message.value)
    }

    private fun pdfDocumentInfo() = DocumentInfo(
        document = TestDocuments.pdf(location = "/test/book.pdf"),
        type = DocumentType.PDF,
        exists = true,
        sizeBytes = 1024L,
    )

    /** A server book on the shelf, not on the device yet. */
    private fun waitingDocumentInfo() = pdfDocumentInfo().copy(exists = false, onServer = true)

    @Test
    fun aDownloadReportsItsProgressAndShowsTheBookOnceItLands() = runTest {
        val repository = FakeLibraryRepository(initialDocuments = listOf(waitingDocumentInfo()))
        val progress = mutableListOf<BookDownload>()
        lateinit var viewModel: BookDetailViewModel
        viewModel = createViewModel(
            location = "/test/book.pdf",
            repository = repository,
            downloader = { _, onProgress ->
                onProgress(0.5f)
                progress += viewModel.download.value
                repository.addDocument(waitingDocumentInfo().copy(exists = true))
            },
        )

        viewModel.startDownload()

        assertEquals(listOf<BookDownload>(BookDownload.Running(0.5f)), progress)
        assertEquals(BookDownload.Idle, viewModel.download.value)
        assertEquals(true, viewModel.uiState.value.dataOrNull?.document?.exists)
    }

    @Test
    fun aDownloadThatFailsOffersToTryAgain() = runTest {
        val repository = FakeLibraryRepository(initialDocuments = listOf(waitingDocumentInfo()))
        val viewModel = createViewModel(
            location = "/test/book.pdf",
            repository = repository,
            downloader = { _, _ -> throw AppErrorException(AppError.Unexpected()) },
        )

        viewModel.startDownload()

        assertEquals(BookDownload.Failed, viewModel.download.value)
        assertEquals(false, viewModel.uiState.value.dataOrNull?.document?.exists)
    }

    @Test
    fun aCancelledDownloadGoesBackToTheButton() = runTest {
        val repository = FakeLibraryRepository(initialDocuments = listOf(waitingDocumentInfo()))
        val viewModel = createViewModel(
            location = "/test/book.pdf",
            repository = repository,
            downloader = { _, _ -> awaitCancellation() },
        )

        viewModel.startDownload()
        assertEquals(BookDownload.Running(0f), viewModel.download.value)

        viewModel.cancelDownload()

        assertEquals(BookDownload.Idle, viewModel.download.value)
    }

    private fun createViewModel(
        location: String,
        repository: LibraryRepository,
        analyzer: DocumentAnalyzer = TypeFromExtensionAnalyzer(),
        downloader: ServerBookDownloader = ServerBookDownloader { _, _ -> error("no server here") },
        shelfChanges: ShelfChanges = ShelfChanges(),
    ): BookDetailViewModel = BookDetailViewModel(
        location = location,
        getDocumentDetailsUseCase = GetDocumentDetailsUseCase(
            repository,
            FakeReadingProgressRepository(),
        ),
        setFavoriteUseCase = SetFavoriteUseCase(repository),
        refreshDocumentUseCase = RefreshDocumentUseCase(
            libraryRepository = repository,
            importDocumentUseCase = ImportDocumentUseCase(
                documentAnalyzer = analyzer,
                libraryRepository = repository,
                dispatcher = UnconfinedTestDispatcher(),
            ),
            documentAnalyzer = analyzer,
            dispatcher = UnconfinedTestDispatcher(),
        ),
        removeDocumentUseCase = RemoveDocumentUseCase(
            repository,
            FakeDocumentWorkspace(),
            FakeIncomingDocumentStore(),
            FakeReadingProgressRepository(),
            FakePdfLayoutRepository(),
        ),
        downloader = downloader,
        shelfChanges = shelfChanges,
    )

    private class FakeLibraryRepository(
        initialDocuments: List<DocumentInfo> = emptyList(),
    ) : LibraryRepository {
        private val documents = initialDocuments.toMutableList()

        /**
         * Replaces by location, and keeps the favourite through it, as the real
         * store does - the flag belongs to the reader, not to the file.
         */
        override suspend fun addDocument(document: DocumentInfo) {
            val index = documents.indexOfFirst {
                it.document.location == document.document.location
            }

            if (index >= 0) {
                documents[index] = document.copy(isFavorite = documents[index].isFavorite)
            } else {
                documents.add(document)
            }
        }

        override suspend fun getDocuments(): List<DocumentInfo> =
            documents.map { it.copy(coverImage = null) }

        override suspend fun getDocument(location: String): DocumentInfo? = documents.firstOrNull {
            it.document.location == location
        }

        /** Read by location, exactly as the real store reads it. */
        override suspend fun coverFor(location: String): CoverImage? =
            getDocument(location)?.coverImage

        /** Answered the expensive way; nothing in a test cares what it costs. */
        override suspend fun knownLocations(): Set<String> =
            getDocuments().mapTo(mutableSetOf()) { it.document.location }

        override suspend fun setFavorite(location: String, isFavorite: Boolean) {
            val index = documents.indexOfFirst { it.document.location == location }
            if (index >= 0) {
                documents[index] = documents[index].copy(isFavorite = isFavorite)
            }
        }

        override suspend fun removeDocument(location: String) {
            documents.removeAll { it.document.location == location }
        }
    }

    private class ThrowingLibraryRepository : LibraryRepository {
        override suspend fun addDocument(document: DocumentInfo) = Unit

        override suspend fun getDocuments(): List<DocumentInfo> = emptyList()

        override suspend fun getDocument(location: String): DocumentInfo? = error("detail read blew up")

        /** Read by location, exactly as the real store reads it. */
        override suspend fun coverFor(location: String): CoverImage? =
            getDocument(location)?.coverImage

        /** Answered the expensive way; nothing in a test cares what it costs. */
        override suspend fun knownLocations(): Set<String> =
            getDocuments().mapTo(mutableSetOf()) { it.document.location }

        override suspend fun setFavorite(location: String, isFavorite: Boolean) = Unit

        override suspend fun removeDocument(location: String) = Unit
    }
}
