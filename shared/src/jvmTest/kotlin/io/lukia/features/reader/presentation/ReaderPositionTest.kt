package io.lukia.features.reader.presentation

import io.lukia.core.domain.reading.FakeReadingSessionRepository
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.features.reader.application.OpenDocumentUseCase
import io.lukia.features.reader.infrastructure.PersistedReaderSettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Where a book opens, and where the reader leaving it puts them back.
 */
class ReaderPositionTest : ReaderViewModelTestCase() {

    @Test
    fun anUnreadBookOpensAtItsFirstPage() = runTest {
        val viewModel = createViewModel(pdfInLibrary(pageCount = 20))
        viewModel.settled()

        assertEquals(0, viewModel.initialPosition.first { it != null }?.pageIndex)
    }

    @Test
    fun aBookOpensWhereItWasLeftOff() = runTest {
        val document = pdfInLibrary(pageCount = 20)
        progress.save(
            location = document.document.location,
            progress = ReadingProgress(pageIndex = 11, pageCount = 20),
        )

        val viewModel = createViewModel(document)
        viewModel.settled()

        assertEquals(11, viewModel.initialPosition.first { it != null }?.pageIndex)
    }

    @Test
    fun whereTheReaderStopsIsWhatIsStored() = runTest {
        val document = pdfInLibrary(pageCount = 20)
        val viewModel = createViewModel(document)
        viewModel.settled()

        viewModel.onPageSettled(pageIndex = 7, pageCount = 20)

        // Field by field rather than as a whole value: the store also stamps
        // where the book stands against the others, which is its business
        // rather than the reader's.
        val stored = progress.progressFor(document.document.location)

        assertEquals(7, stored?.pageIndex)
        assertEquals(20, stored?.pageCount)
    }

    /**
     * A file can be replaced by a shorter one under the same name, and a page
     * that no longer exists is not a place to open at.
     */
    @Test
    fun aStoredPagePastTheEndOfTheBookOpensAtTheLastPage() = runTest {
        val document = pdfInLibrary(pageCount = 5)
        progress.save(
            location = document.document.location,
            progress = ReadingProgress(pageIndex = 400, pageCount = 500),
        )

        val viewModel = createViewModel(document)
        viewModel.settled()

        assertEquals(4, viewModel.initialPosition.first { it != null }?.pageIndex)
    }

    /** Nothing is stored for a book that never opened - there is no page to store. */
    @Test
    fun aBookThatFailedToOpenRecordsNoPosition() = runTest {
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
        viewModel.settled()

        viewModel.onPageSettled(pageIndex = 3, pageCount = 20)

        assertNull(progress.progressFor("/library/missing.pdf"))
    }
}
