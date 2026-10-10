package io.lukia.features.reader.presentation

import io.lukia.core.domain.AppError
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.reading.FakeReadingSessionRepository
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.reader.application.OpenDocumentUseCase
import io.lukia.features.reader.domain.outline.ReaderOutline
import io.lukia.features.reader.infrastructure.PersistedReaderSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Opening a book, and what the ViewModel offers once it is open.
 */
class ReaderViewModelTest : ReaderViewModelTestCase() {

    @Test
    fun opensTheDocumentOnInit() = runTest {
        val document = pdfInLibrary(pageCount = 5)
        val viewModel = createViewModel(document)

        val opened = viewModel.settled().dataOrNull

        assertNotNull(opened)
        assertEquals(5, opened.content.partCount)
    }

    /** Reading has to happen against the copy, never the imported file. */
    @Test
    fun readsFromTheWorkingCopyRatherThanTheOriginal() = runTest {
        val document = pdfInLibrary(pageCount = 1)
        val viewModel = createViewModel(document)

        val opened = assertNotNull(viewModel.settled().dataOrNull)

        assertEquals(document.document.location, opened.workingCopy.sourceLocation)
        assertTrue(opened.workingCopy.path != document.document.location)
        assertTrue(File(opened.workingCopy.path).isFile)
    }

    @Test
    fun aLocationWithNoMatchBecomesDocumentNotFound() = runTest {
        val viewModel = ReaderViewModel(
            location = "/library/missing.pdf",
            openDocumentUseCase = OpenDocumentUseCase(
                catalogue = FakeLibraryRepository(),
                documentWorkspace = workspace,
                dispatcher = openDispatcher,
            ),
            settingsRepository = PersistedReaderSettingsRepository(FakeSettingsStore()),
            reading = BookReadingFactory(progress, FakeReadingSessionRepository()),
            layoutRepository = layouts,
            translations = testTranslations(),
        ).also { track(it) }

        assertEquals(UiState.Failure(AppError.DocumentNotFound), viewModel.settled())
    }

    /**
     * An EPUB is a book the library legitimately holds and the reader cannot
     * open yet, which is not the same as a file the app never accepted.
     */
    @Test
    fun aFormatTheReaderCannotOpenYetSaysSo() = runTest {
        val epub = File(library, "book.epub").apply { writeText("not read yet") }
        val document = DocumentInfo(
            document = PickedDocument(
                fileName = epub.name,
                extension = "epub",
                location = epub.absolutePath,
            ),
            type = DocumentType.EPUB,
            exists = true,
            sizeBytes = epub.length(),
        )

        val viewModel = createViewModel(document)

        assertEquals(UiState.Failure(AppError.FormatNotReadable), viewModel.settled())
    }

    @Test
    fun aFileThatCannotBeOpenedBecomesAFailure() = runTest {
        val broken = File(library, "broken.pdf").apply { writeText("not a pdf") }
        val document = DocumentInfo(
            document = PickedDocument(
                fileName = broken.name,
                extension = "pdf",
                location = broken.absolutePath,
            ),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = broken.length(),
        )

        val viewModel = createViewModel(document)

        assertTrue(viewModel.settled() is UiState.Failure)
    }

    @Test
    fun retryReopensTheDocument() = runTest {
        val document = pdfInLibrary(pageCount = 2)
        val repository = FakeLibraryRepository()
        val viewModel = ReaderViewModel(
            location = document.document.location,
            openDocumentUseCase = OpenDocumentUseCase(
                catalogue = repository,
                documentWorkspace = workspace,
                dispatcher = openDispatcher,
            ),
            settingsRepository = PersistedReaderSettingsRepository(FakeSettingsStore()),
            reading = BookReadingFactory(progress, FakeReadingSessionRepository()),
            layoutRepository = layouts,
            translations = testTranslations(),
        ).also { track(it) }

        assertEquals(UiState.Failure(AppError.DocumentNotFound), viewModel.settled())

        repository.addDocument(document)
        viewModel.retry()

        assertEquals(2, viewModel.settled().dataOrNull?.content?.partCount)
    }

    @Test
    fun theOutlineLoadsWithoutBeingAskedFor() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 3, chapters = 2))
        viewModel.settled()

        val outline = viewModel.outline.first { it !is ReaderOutline.Loading }

        assertEquals(
            listOf("Chapter 1", "Chapter 2"),
            (outline as? ReaderOutline.Chapters)?.entries?.map { it.title },
        )
    }

    @Test
    fun aBookWithNoChaptersReportsAnEmptyOutline() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 3))
        viewModel.settled()

        val outline = viewModel.outline.first { it !is ReaderOutline.Loading }

        assertEquals(ReaderOutline.Chapters(emptyList()), outline)
    }

    /** A book nobody has opened starts at the beginning, like a new book. */

    @Test
    fun settingsChangesAreVisibleThroughTheViewModel() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 1))
        viewModel.settled()

        viewModel.updateSettings(viewModel.settings.value.withLargerText())

        assertEquals(110, viewModel.settings.value.textScalePercent)
    }

    /** A resized window invalidates every page drawn for the old one. */
    @Test
    fun aPageIsRedrawnWhenTheWidthChanges() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 1))
        viewModel.settled()

        val narrow = viewModel.page(index = 0, widthPx = 384)
        val wide = viewModel.page(index = 0, widthPx = 640)

        assertEquals(384, narrow?.width)
        assertEquals(640, wide?.width)
    }

    @Test
    fun anUnopenedDocumentHasNoPagesToDraw() = runTest {
        val viewModel = ReaderViewModel(
            location = "/library/missing.pdf",
            openDocumentUseCase = OpenDocumentUseCase(
                catalogue = FakeLibraryRepository(),
                documentWorkspace = workspace,
                dispatcher = openDispatcher,
            ),
            settingsRepository = PersistedReaderSettingsRepository(FakeSettingsStore()),
            reading = BookReadingFactory(progress, FakeReadingSessionRepository()),
            layoutRepository = layouts,
            translations = testTranslations(),
        ).also { track(it) }

        assertNull(viewModel.page(index = 0, widthPx = 384))
    }

    /** Registers a ViewModel so tear-down can cancel its scope. */
}
