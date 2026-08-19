package io.lukia.features.library.presentation

import io.lukia.MainDispatcherTest
import io.lukia.core.domain.AppError
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.TestDocuments
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.displayShortMessage
import io.lukia.features.library.TypeFromExtensionAnalyzer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

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
            AppError.UnsupportedFileType.displayShortMessage(),
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

        assertEquals("analysis blew up", viewModel.message.value)
        assertFalse(viewModel.isImporting.value)
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
            folderScanner = FakeFolderScanner(listOf(documentInfo.document)),
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

        assertEquals(AppError.EmptyFolder.displayShortMessage(), viewModel.message.value)
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
