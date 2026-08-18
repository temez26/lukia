package io.lukia.features.bookdetail.presentation

import io.lukia.core.domain.AppError
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.FakeDocumentWorkspace
import io.lukia.features.reader.domain.FakeReadingProgressRepository
import io.lukia.core.domain.TestDocuments
import io.lukia.core.presentation.UiState
import io.lukia.features.bookdetail.application.BookDetails
import io.lukia.features.bookdetail.application.GetDocumentDetailsUseCase
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.library.application.SetFavoriteUseCase
import io.lukia.features.library.domain.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BookDetailViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
            repository = FakeLibraryRepository()
        )

        assertEquals(UiState.Failure(AppError.DocumentNotFound), viewModel.uiState.value)
    }

    @Test
    fun aReadThatThrowsBecomesAnUnexpectedFailure() = runTest {
        val viewModel = createViewModel(
            location = "/test/book.pdf",
            repository = ThrowingLibraryRepository()
        )

        assertEquals(
            UiState.Failure(AppError.Unexpected("detail read blew up")),
            viewModel.uiState.value
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
            viewModel.uiState.value
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
            viewModel.uiState.value
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

    private fun pdfDocumentInfo() = DocumentInfo(
        document = TestDocuments.pdf(location = "/test/book.pdf"),
        type = DocumentType.PDF,
        exists = true,
        sizeBytes = 1024L
    )

    private fun createViewModel(
        location: String,
        repository: LibraryRepository
    ): BookDetailViewModel {
        return BookDetailViewModel(
            location = location,
            getDocumentDetailsUseCase = GetDocumentDetailsUseCase(
                repository,
                FakeReadingProgressRepository()
            ),
            setFavoriteUseCase = SetFavoriteUseCase(repository),
            removeDocumentUseCase = RemoveDocumentUseCase(
                repository,
                FakeDocumentWorkspace(),
                FakeReadingProgressRepository()
            )
        )
    }

    private class FakeLibraryRepository(
        initialDocuments: List<DocumentInfo> = emptyList()
    ) : LibraryRepository {
        private val documents = initialDocuments.toMutableList()

        override suspend fun addDocument(document: DocumentInfo) {
            documents.add(document)
        }

        override suspend fun getDocuments(): List<DocumentInfo> {
            return documents.toList()
        }

        override suspend fun getDocument(location: String): DocumentInfo? {
            return documents.firstOrNull { it.document.location == location }
        }

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

        override suspend fun getDocument(location: String): DocumentInfo? {
            throw IllegalStateException("detail read blew up")
        }

        /** Answered the expensive way; nothing in a test cares what it costs. */
        override suspend fun knownLocations(): Set<String> =
            getDocuments().mapTo(mutableSetOf()) { it.document.location }

        override suspend fun setFavorite(location: String, isFavorite: Boolean) = Unit

        override suspend fun removeDocument(location: String) = Unit
    }
}
