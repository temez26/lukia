package io.lukia.features.library.presentation

import io.lukia.MainDispatcherTest
import io.lukia.core.domain.AppError
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.TestDocuments
import io.lukia.core.presentation.UiState
import io.lukia.features.library.TypeFromExtensionAnalyzer
import io.lukia.features.library.presentation.model.LibraryMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Putting books into the library: one file, a folder of them, and every way
 * either can go wrong without taking the library off the screen.
 */
class LibraryImportTest : MainDispatcherTest() {

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
                sizeBytes = 10L,
            ),
        )
        val viewModel = createViewModel(FakeLibraryRepository(), analyzer)

        viewModel.importDocuments(listOf(TestDocuments.unsupported(location = "/test/book.mobi")))

        assertEquals(emptyList(), viewModel.documents())
        assertEquals(
            LibraryMessage.Failed(AppError.UnsupportedFileType),
            viewModel.message.value,
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
            analyzer = ThrowingDocumentAnalyzer(),
        )

        viewModel.importDocuments(listOf(TestDocuments.pdf(location = "/test/book.pdf")))

        assertEquals(LibraryMessage.Failed(AppError.Unexpected()), viewModel.message.value)
        assertFalse(viewModel.isImporting.value)
    }

    /**
     * A folder can hold thousands of books, each one opened and its cover
     * rendered, and until this the only way out of one was to kill the app -
     * which stops at the same book and says nothing about how far it got.
     */
    @Test
    fun stoppingAnImportKeepsTheBooksThatAlreadyArrived() = runTest {
        val arrived = pdfDocumentInfo(location = "/test/one.pdf")
        val repository = FakeLibraryRepository()

        val viewModel = createViewModel(
            repository = repository,
            analyzer = HaltingDocumentAnalyzer(
                haltAt = "/test/two.pdf",
                halted = CompletableDeferred(),
            ),
        )

        viewModel.importDocuments(
            listOf(arrived.document, TestDocuments.pdf(location = "/test/two.pdf")),
        )

        assertTrue(viewModel.isImporting.value)

        viewModel.cancelImport()

        assertFalse(viewModel.isImporting.value)
        assertEquals(listOf(arrived), viewModel.documents())
    }

    /** An import failing must not blank out a library the user can still read. */
    @Test
    fun importFailureLeavesTheLibraryOnScreen() = runTest {
        val documentInfo = pdfDocumentInfo()
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(initialDocuments = listOf(documentInfo)),
            analyzer = ThrowingDocumentAnalyzer(),
        )

        viewModel.importDocuments(listOf(TestDocuments.pdf(location = "/test/other.pdf")))

        assertEquals(UiState.Success(listOf(documentInfo)), viewModel.uiState.value)
    }

    @Test
    fun isImportingIsClearedOnceAnImportSucceeds() = runTest {
        val documentInfo = pdfDocumentInfo()
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(),
            analyzer = FakeDocumentAnalyzer(documentInfo),
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
            folderScanner = FakeFolderScanner(listOf(documentInfo.document)),
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
                    TestDocuments.unsupported(location = "/test/notes.mobi"),
                ),
            ),
        )

        viewModel.importFolder(booksFolder)

        assertEquals(1, viewModel.documents().size)
        assertEquals(
            LibraryMessage.Imported(imported = 1, alreadyInLibrary = 0, failed = 1),
            viewModel.message.value,
        )
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
            folderScanner = FakeFolderScanner(listOf(documentInfo.document)),
        )

        viewModel.importFolder(booksFolder)

        assertEquals(LibraryMessage.AlreadyInLibrary(1), viewModel.message.value)
    }

    /** Nothing counts a batch of one, so nothing should show a count for it. */
    @Test
    fun aSingleFileImportShowsNoProgressCount() = runTest {
        val documentInfo = pdfDocumentInfo()
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(),
            analyzer = FakeDocumentAnalyzer(documentInfo),
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
                    TestDocuments.pdf(location = "/test/two.pdf"),
                ),
            ),
        )

        viewModel.importFolder(booksFolder)

        assertNull(viewModel.importProgress.value)
    }

    @Test
    fun emptyFolderReportsATypedError() = runTest {
        val viewModel = createViewModel(
            repository = FakeLibraryRepository(),
            folderScanner = FakeFolderScanner(emptyList()),
        )

        viewModel.importFolder(booksFolder)

        assertEquals(LibraryMessage.Failed(AppError.EmptyFolder), viewModel.message.value)
    }

    @Test
    fun clearMessageResetsTheNotice() = runTest {
        val analyzer = FakeDocumentAnalyzer(
            DocumentInfo(
                document = TestDocuments.unsupported(location = "/test/book.mobi"),
                type = DocumentType.UNKNOWN,
                exists = true,
                sizeBytes = 10L,
            ),
        )
        val viewModel = createViewModel(FakeLibraryRepository(), analyzer)
        viewModel.importDocuments(listOf(TestDocuments.unsupported(location = "/test/book.mobi")))

        viewModel.clearMessage()

        assertNull(viewModel.message.value)
    }
}
