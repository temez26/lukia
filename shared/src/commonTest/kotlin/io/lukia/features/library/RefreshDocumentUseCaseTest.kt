package io.lukia.features.library

import io.lukia.core.domain.AppError
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.document.TestDocuments
import io.lukia.core.domain.toAppError
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.features.library.application.RefreshDocumentUseCase
import io.lukia.features.library.domain.DocumentAnalyzer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshDocumentUseCaseTest {

    private val book = TestDocuments.pdf(location = "/test/book.pdf")

    @Test
    fun readsTheFileAgainAndStoresWhatItFinds() = runTest {
        val repository = FakeImportLibraryRepository(listOf(storedAs(sizeBytes = 1L)))

        val result = createUseCase(
            repository,
            analyzer = FixedAnalyzer(storedAs(sizeBytes = 4096L)),
        )(book.location)

        assertEquals(4096L, result.getOrThrow().sizeBytes)
        assertEquals(4096L, repository.getDocument(book.location)?.sizeBytes)
    }

    /** One row in, one row out: a refresh must never add a second copy. */
    @Test
    fun replacesTheEntryRatherThanAddingAnother() = runTest {
        val repository = FakeImportLibraryRepository(listOf(storedAs(sizeBytes = 1L)))

        createUseCase(repository, analyzer = FixedAnalyzer(storedAs(sizeBytes = 2L)))(
            book.location,
        )

        assertEquals(1, repository.documents.size)
    }

    /** The flag belongs to the reader; re-reading the file must not clear it. */
    @Test
    fun keepsTheFavouriteThroughARefresh() = runTest {
        val repository = FakeImportLibraryRepository(
            listOf(storedAs(sizeBytes = 1L).copy(isFavorite = true)),
        )

        val result = createUseCase(
            repository,
            analyzer = FixedAnalyzer(storedAs(sizeBytes = 2L)),
        )(book.location)

        assertTrue(result.getOrThrow().isFavorite)
    }

    @Test
    fun aBookTheLibraryDoesNotHoldIsNotFound() = runTest {
        val repository = FakeImportLibraryRepository()

        val result = createUseCase(repository)("/test/never-imported.pdf")

        assertFalse(result.isSuccess)
        assertEquals(AppError.DocumentNotFound, result.exceptionOrNull()?.toAppError())
    }

    /**
     * A file that has gone missing since it was imported leaves the entry as it
     * was rather than storing an empty reading over a good one.
     *
     * It throws rather than returning a failure because the analyzer does, which
     * is the same path an import takes - the ViewModel is where an unforeseen
     * throw is narrowed into something the reader is shown.
     */
    @Test
    fun aFileThatCanNoLongerBeReadLeavesTheEntryAlone() = runTest {
        val repository = FakeImportLibraryRepository(listOf(storedAs(sizeBytes = 1L)))

        val thrown = runCatching {
            createUseCase(
                repository,
                analyzer = TypeFromExtensionAnalyzer(failingLocations = setOf(book.location)),
            )(book.location)
        }

        assertTrue(thrown.isFailure)
        assertEquals(1L, repository.getDocument(book.location)?.sizeBytes)
    }

    private fun storedAs(sizeBytes: Long) = DocumentInfo(
        document = book,
        type = DocumentType.PDF,
        exists = true,
        sizeBytes = sizeBytes,
    )

    private fun TestScope.createUseCase(
        repository: FakeImportLibraryRepository,
        analyzer: DocumentAnalyzer = TypeFromExtensionAnalyzer(),
    ) = RefreshDocumentUseCase(
        libraryRepository = repository,
        importDocumentUseCase = ImportDocumentUseCase(
            documentAnalyzer = analyzer,
            libraryRepository = repository,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        ),
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    /** Stands in for a second reading of the same file that found something new. */
    private class FixedAnalyzer(
        private val documentInfo: DocumentInfo,
    ) : DocumentAnalyzer {
        override suspend fun analyze(document: PickedDocument): DocumentInfo = documentInfo
    }
}
