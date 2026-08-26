package io.lukia.features.library

import io.lukia.core.domain.AppError
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder
import io.lukia.core.domain.TestDocuments
import io.lukia.core.domain.toAppError
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.features.library.application.ImportDocumentsUseCase
import io.lukia.features.library.application.ImportFolderUseCase
import io.lukia.features.library.application.ImportSummary
import io.lukia.features.library.domain.FolderScanner
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportFolderUseCaseTest {

    private val folder = PickedFolder(
        name = "Books",
        location = "/test/books",
    )

    @Test
    fun importsEveryScannedDocument() = runTest {
        val documents = listOf(
            TestDocuments.pdf(location = "/test/books/book.pdf"),
            TestDocuments.epub(location = "/test/books/series/book.epub"),
        )

        val repository = FakeImportLibraryRepository()
        val useCase = createUseCase(
            scanner = FakeFolderScanner(documents),
            repository = repository,
        )

        val result = useCase(folder)

        assertTrue(result.isSuccess)
        assertEquals(
            ImportSummary(imported = 2, alreadyInLibrary = 0, failed = 0),
            result.getOrThrow(),
        )
        assertEquals(documents, repository.documents.map { it.document })
    }

    @Test
    fun failsWhenFolderHasNoSupportedDocuments() = runTest {
        val repository = FakeImportLibraryRepository()
        val useCase = createUseCase(
            scanner = FakeFolderScanner(emptyList()),
            repository = repository,
        )

        val result = useCase(folder)

        assertFalse(result.isSuccess)
        assertEquals(
            AppError.EmptyFolder,
            result.exceptionOrNull()?.toAppError(),
        )
        assertTrue(repository.documents.isEmpty())
    }

    /** A folder is scanned whole, so the count has to describe the whole of it. */
    @Test
    fun reportsProgressOverTheWholeFolder() = runTest {
        val documents = listOf(
            TestDocuments.pdf(location = "/test/books/one.pdf"),
            TestDocuments.pdf(location = "/test/books/two.pdf"),
        )

        val seen = mutableListOf<Pair<Int, Int>>()

        val useCase = createUseCase(
            scanner = FakeFolderScanner(documents),
            repository = FakeImportLibraryRepository(),
        )

        useCase(folder) { completed, total -> seen += completed to total }

        assertEquals(listOf(1 to 2, 2 to 2), seen)
    }

    private fun TestScope.createUseCase(
        scanner: FolderScanner,
        repository: FakeImportLibraryRepository,
    ) = ImportFolderUseCase(
        folderScanner = scanner,
        importDocumentsUseCase = ImportDocumentsUseCase(
            importDocumentUseCase = ImportDocumentUseCase(
                documentAnalyzer = TypeFromExtensionAnalyzer(),
                libraryRepository = repository,
                dispatcher = UnconfinedTestDispatcher(testScheduler),
            ),
            libraryRepository = repository,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        ),
        dispatcher = UnconfinedTestDispatcher(testScheduler),
    )

    private class FakeFolderScanner(
        private val documents: List<PickedDocument>,
    ) : FolderScanner {

        override suspend fun scan(folder: PickedFolder): List<PickedDocument> = documents
    }
}
