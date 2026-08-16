package io.lukia.features.library.presentation

import io.lukia.core.domain.AppError
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.FakeDocumentWorkspace
import io.lukia.features.reader.domain.FakeReadingProgressRepository
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder
import io.lukia.core.domain.TestDocuments
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.displayShortMessage
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.FolderScanner
import io.lukia.features.library.application.GetLibraryDocumentsUseCase
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.features.library.application.ImportFolderUseCase
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
import kotlin.test.assertFalse
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

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
            viewModel.uiState.value
        )
    }

    @Test
    fun importingDocumentReloadsLibraryAndClearsMessage() = runTest {
        val documentInfo = pdfDocumentInfo()
        val repository = FakeLibraryRepository()
        val analyzer = FakeDocumentAnalyzer(documentInfo)
        val viewModel = createViewModel(repository, analyzer)

        viewModel.importDocument(documentInfo.document)

        assertEquals(listOf(documentInfo), viewModel.documents())
        assertNull(viewModel.message.value)
    }

    @Test
    fun unsupportedFileReportsATypedErrorAndKeepsLibraryUnchanged() = runTest {
        val analyzer = FakeDocumentAnalyzer(
            DocumentInfo(
                document = TestDocuments.txt(location = "/test/book.txt"),
                type = DocumentType.UNKNOWN,
                exists = true,
                sizeBytes = 10L
            )
        )
        val viewModel = createViewModel(FakeLibraryRepository(), analyzer)

        viewModel.importDocument(TestDocuments.txt(location = "/test/book.txt"))

        assertEquals(emptyList(), viewModel.documents())
        assertEquals(
            AppError.UnsupportedFileType.displayShortMessage(),
            viewModel.message.value
        )
    }

    /**
     * A failure the import path never anticipated has to reach the user as a
     * message and clear the spinner, rather than crashing inside viewModelScope
     * and leaving the library importing forever.
     */
    @Test
    fun analyzerFailureIsReportedAndClearsIsImporting() = runTest {
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(),
            analyzer = ThrowingDocumentAnalyzer()
        )

        viewModel.importDocument(TestDocuments.pdf(location = "/test/book.pdf"))

        assertEquals("analysis blew up", viewModel.message.value)
        assertFalse(viewModel.isImporting.value)
    }

    /** An import failing must not blank out a library the user can still read. */
    @Test
    fun importFailureLeavesTheLibraryOnScreen() = runTest {
        val documentInfo = pdfDocumentInfo()
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo)),
            analyzer = ThrowingDocumentAnalyzer()
        )

        viewModel.importDocument(TestDocuments.pdf(location = "/test/other.pdf"))

        assertEquals(UiState.Success(listOf(documentInfo)), viewModel.uiState.value)
    }

    @Test
    fun isImportingIsClearedOnceAnImportSucceeds() = runTest {
        val documentInfo = pdfDocumentInfo()
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(),
            analyzer = FakeDocumentAnalyzer(documentInfo)
        )

        assertFalse(viewModel.isImporting.value)

        viewModel.importDocument(documentInfo.document)

        assertFalse(viewModel.isImporting.value)
    }

    @Test
    fun importingFolderReloadsLibraryAndClearsMessage() = runTest {
        val documentInfo = pdfDocumentInfo()
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(),
            analyzer = FakeDocumentAnalyzer(documentInfo),
            folderScanner = FakeFolderScanner(listOf(documentInfo.document))
        )

        viewModel.importFolder(booksFolder)

        assertEquals(listOf(documentInfo), viewModel.documents())
        assertNull(viewModel.message.value)
    }

    @Test
    fun partialFolderImportReportsSkippedFiles() = runTest {
        val documentInfo = pdfDocumentInfo()
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(),
            // Every file analyzes to the same unsupported result, so the whole
            // folder is skipped rather than imported.
            analyzer = FakeDocumentAnalyzer(
                DocumentInfo(
                    document = TestDocuments.txt(location = "/test/notes.txt"),
                    type = DocumentType.UNKNOWN,
                    exists = true,
                    sizeBytes = 10L
                )
            ),
            folderScanner = FakeFolderScanner(listOf(documentInfo.document))
        )

        viewModel.importFolder(booksFolder)

        assertEquals(emptyList(), viewModel.documents())
        assertEquals("Imported 0, skipped 1", viewModel.message.value)
    }

    @Test
    fun emptyFolderReportsATypedError() = runTest {
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(),
            folderScanner = FakeFolderScanner(emptyList())
        )

        viewModel.importFolder(booksFolder)

        assertEquals(AppError.EmptyFolder.displayShortMessage(), viewModel.message.value)
    }

    @Test
    fun clearMessageResetsTheNotice() = runTest {
        val analyzer = FakeDocumentAnalyzer(
            DocumentInfo(
                document = TestDocuments.txt(location = "/test/book.txt"),
                type = DocumentType.UNKNOWN,
                exists = true,
                sizeBytes = 10L
            )
        )
        val viewModel = createViewModel(FakeLibraryRepository(), analyzer)
        viewModel.importDocument(TestDocuments.txt(location = "/test/book.txt"))

        viewModel.clearMessage()

        assertNull(viewModel.message.value)
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

    @Test
    fun deleteDocumentRemovesItFromTheLibrary() = runTest {
        val documentInfo = pdfDocumentInfo()
        val repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo))
        val viewModel = createViewModel(repository)

        viewModel.deleteDocument(documentInfo)

        assertEquals(emptyList(), viewModel.documents())
    }

    /** Fails the assertion rather than returning null when the state is not Success. */
    private fun LibraryViewModel.documents(): List<DocumentInfo> {
        val state = uiState.value
        return (state as? UiState.Success)?.data
            ?: throw AssertionError("expected a Success state, was $state")
    }

    private fun createViewModel(
        repository: LibraryRepository,
        analyzer: DocumentAnalyzer = FakeDocumentAnalyzer(pdfDocumentInfo()),
        folderScanner: FolderScanner = FakeFolderScanner(emptyList())
    ): LibraryViewModel {
        // The use cases move work off the main dispatcher in production;
        // pinning it here keeps imports synchronous so state can be asserted
        // immediately after the call.
        val importDocumentUseCase = ImportDocumentUseCase(
            documentAnalyzer = analyzer,
            libraryRepository = repository,
            dispatcher = UnconfinedTestDispatcher()
        )

        return LibraryViewModel(
            getLibraryDocumentsUseCase = GetLibraryDocumentsUseCase(repository),
            importDocumentUseCase = importDocumentUseCase,
            importFolderUseCase = ImportFolderUseCase(
                folderScanner = folderScanner,
                importDocumentUseCase = importDocumentUseCase,
                dispatcher = UnconfinedTestDispatcher()
            ),
            setFavoriteUseCase = SetFavoriteUseCase(repository),
            removeDocumentUseCase = RemoveDocumentUseCase(
                repository,
                FakeDocumentWorkspace(),
                FakeReadingProgressRepository()
            )
        )
    }

    private val booksFolder = PickedFolder(
        name = "Books",
        location = "/test"
    )

    private fun pdfDocumentInfo() = DocumentInfo(
        document = TestDocuments.pdf(location = "/test/book.pdf"),
        type = DocumentType.PDF,
        exists = true,
        sizeBytes = 1024L
    )

    private class FakeDocumentAnalyzer(
        private val documentInfo: DocumentInfo
    ) : DocumentAnalyzer {
        override suspend fun analyze(document: PickedDocument): DocumentInfo {
            return documentInfo
        }
    }

    private class ThrowingDocumentAnalyzer : DocumentAnalyzer {
        override suspend fun analyze(document: PickedDocument): DocumentInfo {
            throw IllegalStateException("analysis blew up")
        }
    }

    private class FakeFolderScanner(
        private val documents: List<PickedDocument>
    ) : FolderScanner {
        override suspend fun scan(folder: PickedFolder): List<PickedDocument> {
            return documents
        }
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

        override suspend fun getDocuments(): List<DocumentInfo> {
            throw IllegalStateException("library read blew up")
        }

        override suspend fun getDocument(location: String): DocumentInfo? = null

        override suspend fun setFavorite(location: String, isFavorite: Boolean) = Unit

        override suspend fun removeDocument(location: String) = Unit
    }
}
