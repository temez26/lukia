package io.lukia.features.library.presentation

import io.lukia.core.domain.AppError
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.FakeDocumentWorkspace
import io.lukia.features.reader.domain.FakeReadingProgressRepository
import io.lukia.features.reader.domain.ReadingProgress
import io.lukia.features.reader.domain.ReadingProgressRepository
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder
import io.lukia.core.domain.TestDocuments
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.displayShortMessage
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.FolderScanner
import io.lukia.features.library.application.GetLibraryDocumentsUseCase
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.features.library.application.ImportDocumentsUseCase
import io.lukia.features.library.application.ImportFolderUseCase
import io.lukia.features.library.application.RefreshDocumentUseCase
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.library.application.SetFavoriteUseCase
import io.lukia.features.library.TypeFromExtensionAnalyzer
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

        viewModel.importDocuments(listOf(documentInfo.document))

        assertEquals(listOf(documentInfo), viewModel.documents())
        assertNull(viewModel.message.value)
    }

    @Test
    fun unsupportedFileReportsATypedErrorAndKeepsLibraryUnchanged() = runTest {
        val analyzer = FakeDocumentAnalyzer(
            DocumentInfo(
                document = TestDocuments.unsupported(location = "/test/book.mobi"),
                type = DocumentType.UNKNOWN,
                exists = true,
                sizeBytes = 10L
            )
        )
        val viewModel = createViewModel(FakeLibraryRepository(), analyzer)

        viewModel.importDocuments(listOf(TestDocuments.unsupported(location = "/test/book.mobi")))

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

        viewModel.importDocuments(listOf(TestDocuments.pdf(location = "/test/book.pdf")))

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

        viewModel.importDocuments(listOf(TestDocuments.pdf(location = "/test/other.pdf")))

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

        viewModel.importDocuments(listOf(documentInfo.document))

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

    /**
     * A folder that came in partly is not a failure - the books that did arrive
     * are usable - so it reports itself as a notice rather than an error.
     */
    @Test
    fun partialFolderImportReportsSkippedFiles() = runTest {
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(),
            analyzer = TypeFromExtensionAnalyzer(),
            folderScanner = FakeFolderScanner(
                listOf(
                    TestDocuments.pdf(location = "/test/book.pdf"),
                    TestDocuments.unsupported(location = "/test/notes.mobi")
                )
            )
        )

        viewModel.importFolder(booksFolder)

        assertEquals(1, viewModel.documents().size)
        assertEquals("Imported 1, 1 skipped", viewModel.message.value)
    }

    /**
     * A rescan is the common case - the reader added two books to a shelf the
     * library already knows - and it must not re-open every file on it.
     */
    @Test
    fun rescanningAFolderReportsWhatWasAlreadyThere() = runTest {
        val documentInfo = pdfDocumentInfo()
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo)),
            analyzer = ThrowingDocumentAnalyzer(),
            folderScanner = FakeFolderScanner(listOf(documentInfo.document))
        )

        viewModel.importFolder(booksFolder)

        assertEquals("Already in your library", viewModel.message.value)
    }

    /** Nothing counts a batch of one, so nothing should show a count for it. */
    @Test
    fun aSingleFileImportShowsNoProgressCount() = runTest {
        val documentInfo = pdfDocumentInfo()
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(),
            analyzer = FakeDocumentAnalyzer(documentInfo)
        )

        viewModel.importDocuments(listOf(documentInfo.document))

        assertNull(viewModel.importProgress.value)
    }

    /** The count belongs to the import that is running, and to no moment after it. */
    @Test
    fun progressIsClearedOnceAnImportFinishes() = runTest {
        val documentInfo = pdfDocumentInfo()
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(),
            analyzer = FakeDocumentAnalyzer(documentInfo),
            folderScanner = FakeFolderScanner(
                listOf(
                    TestDocuments.pdf(location = "/test/one.pdf"),
                    TestDocuments.pdf(location = "/test/two.pdf")
                )
            )
        )

        viewModel.importFolder(booksFolder)

        assertNull(viewModel.importProgress.value)
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
                document = TestDocuments.unsupported(location = "/test/book.mobi"),
                type = DocumentType.UNKNOWN,
                exists = true,
                sizeBytes = 10L
            )
        )
        val viewModel = createViewModel(FakeLibraryRepository(), analyzer)
        viewModel.importDocuments(listOf(TestDocuments.unsupported(location = "/test/book.mobi")))

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
            analyzer = FakeDocumentAnalyzer(rescanned)
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
            analyzer = ThrowingDocumentAnalyzer()
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
            mutableMapOf("/test/book.pdf" to ReadingProgress(pageIndex = 41, pageCount = 100))
        )

        val viewModel = createViewModel(repository, readingProgress = positions)

        assertEquals(
            ReadingProgress(pageIndex = 41, pageCount = 100),
            viewModel.readingProgress.value["/test/book.pdf"]
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

    private class ThrowingProgressRepository : ReadingProgressRepository {
        override suspend fun progressFor(location: String): ReadingProgress? = null

        override suspend fun allProgress(): Map<String, ReadingProgress> {
            throw IllegalStateException("position store blew up")
        }

        override suspend fun save(location: String, progress: ReadingProgress) = Unit

        override suspend fun forget(location: String) = Unit
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
        folderScanner: FolderScanner = FakeFolderScanner(emptyList()),
        readingProgress: ReadingProgressRepository = FakeReadingProgressRepository()
    ): LibraryViewModel {
        // The use cases move work off the main dispatcher in production;
        // pinning it here keeps imports synchronous so state can be asserted
        // immediately after the call.
        val importDocumentsUseCase = ImportDocumentsUseCase(
            importDocumentUseCase = ImportDocumentUseCase(
                documentAnalyzer = analyzer,
                libraryRepository = repository,
                dispatcher = UnconfinedTestDispatcher()
            ),
            libraryRepository = repository,
            dispatcher = UnconfinedTestDispatcher()
        )

        return LibraryViewModel(
            getLibraryDocumentsUseCase = GetLibraryDocumentsUseCase(repository),
            importDocumentsUseCase = importDocumentsUseCase,
            importFolderUseCase = ImportFolderUseCase(
                folderScanner = folderScanner,
                importDocumentsUseCase = importDocumentsUseCase,
                dispatcher = UnconfinedTestDispatcher()
            ),
            setFavoriteUseCase = SetFavoriteUseCase(repository),
            refreshDocumentUseCase = RefreshDocumentUseCase(
                libraryRepository = repository,
                importDocumentUseCase = ImportDocumentUseCase(
                    documentAnalyzer = analyzer,
                    libraryRepository = repository,
                    dispatcher = UnconfinedTestDispatcher()
                ),
                dispatcher = UnconfinedTestDispatcher()
            ),
            removeDocumentUseCase = RemoveDocumentUseCase(
                repository,
                FakeDocumentWorkspace(),
                FakeReadingProgressRepository()
            ),
            readingProgressRepository = readingProgress
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

        override suspend fun getDocuments(): List<DocumentInfo> {
            throw IllegalStateException("library read blew up")
        }

        override suspend fun getDocument(location: String): DocumentInfo? = null

        /** Answered the expensive way; nothing in a test cares what it costs. */
        override suspend fun knownLocations(): Set<String> =
            getDocuments().mapTo(mutableSetOf()) { it.document.location }

        override suspend fun setFavorite(location: String, isFavorite: Boolean) = Unit

        override suspend fun removeDocument(location: String) = Unit
    }
}
