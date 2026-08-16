package io.lukia.features.library

import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.AppError
import io.lukia.core.domain.toAppError
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder
import io.lukia.core.domain.TestDocuments
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.FolderScanner
import io.lukia.features.library.application.FolderImportSummary
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.features.library.application.ImportFolderUseCase
import io.lukia.features.library.domain.LibraryRepository
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
        location = "/test/books"
    )

    @Test
    fun importsEveryScannedDocument() = runTest {
        val documents = listOf(
            TestDocuments.pdf(location = "/test/books/book.pdf"),
            TestDocuments.epub(location = "/test/books/book.epub")
        )

        val repository = FakeLibraryRepository()
        val useCase = createUseCase(
            scanner = FakeFolderScanner(documents),
            analyzer = TypeFromExtensionAnalyzer(),
            repository = repository
        )

        val result = useCase(folder)

        assertTrue(result.isSuccess)
        assertEquals(FolderImportSummary(imported = 2, failed = 0), result.getOrThrow())
        assertEquals(documents, repository.documents.map { it.document })
    }

    @Test
    fun failsWhenFolderHasNoSupportedDocuments() = runTest {
        val repository = FakeLibraryRepository()
        val useCase = createUseCase(
            scanner = FakeFolderScanner(emptyList()),
            repository = repository
        )

        val result = useCase(folder)

        assertFalse(result.isSuccess)
        assertEquals(
            AppError.EmptyFolder,
            result.exceptionOrNull()?.toAppError()
        )
        assertTrue(repository.documents.isEmpty())
    }

    @Test
    fun countsRejectedDocumentsAsFailedWithoutAbortingTheFolder() = runTest {
        val documents = listOf(
            TestDocuments.pdf(location = "/test/books/book.pdf"),
            TestDocuments.unsupported(location = "/test/books/notes.mobi"),
            TestDocuments.epub(location = "/test/books/book.epub")
        )

        val repository = FakeLibraryRepository()
        val useCase = createUseCase(
            scanner = FakeFolderScanner(documents),
            analyzer = TypeFromExtensionAnalyzer(),
            repository = repository
        )

        val result = useCase(folder)

        assertEquals(FolderImportSummary(imported = 2, failed = 1), result.getOrThrow())
        assertEquals(2, repository.documents.size)
    }

    @Test
    fun analyzerFailureIsCountedAndTheRemainingFilesStillImport() = runTest {
        val unreadable = TestDocuments.missingPdf(location = "/test/books/missing.pdf")
        val readable = TestDocuments.pdf(location = "/test/books/book.pdf")

        val repository = FakeLibraryRepository()
        val useCase = createUseCase(
            scanner = FakeFolderScanner(listOf(unreadable, readable)),
            analyzer = TypeFromExtensionAnalyzer(
                failingLocations = setOf(unreadable.location)
            ),
            repository = repository
        )

        val result = useCase(folder)

        assertEquals(FolderImportSummary(imported = 1, failed = 1), result.getOrThrow())
        assertEquals(listOf(readable), repository.documents.map { it.document })
    }

    private fun TestScope.createUseCase(
        scanner: FolderScanner,
        analyzer: DocumentAnalyzer = TypeFromExtensionAnalyzer(),
        repository: LibraryRepository
    ) = ImportFolderUseCase(
        folderScanner = scanner,
        importDocumentUseCase = ImportDocumentUseCase(
            documentAnalyzer = analyzer,
            libraryRepository = repository,
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        ),
        dispatcher = UnconfinedTestDispatcher(testScheduler)
    )

    private class FakeFolderScanner(
        private val documents: List<PickedDocument>
    ) : FolderScanner {

        override suspend fun scan(folder: PickedFolder): List<PickedDocument> {
            return documents
        }
    }

    /** Stands in for a real analyzer: classifies by extension, and throws for
     * locations a platform analyzer would fail to open. */
    private class TypeFromExtensionAnalyzer(
        private val failingLocations: Set<String> = emptySet()
    ) : DocumentAnalyzer {

        override suspend fun analyze(document: PickedDocument): DocumentInfo {

            if (document.location in failingLocations) {
                throw IllegalArgumentException(
                    "File does not exist: ${document.location}"
                )
            }

            return DocumentInfo(
                document = document,
                type = DocumentType.fromExtension(document.extension),
                exists = true,
                sizeBytes = 1024L
            )
        }
    }

    private class FakeLibraryRepository : LibraryRepository {

        val documents = mutableListOf<DocumentInfo>()

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
}
