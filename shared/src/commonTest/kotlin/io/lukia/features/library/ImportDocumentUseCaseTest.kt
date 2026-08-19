package io.lukia.features.library

import io.lukia.core.domain.AppError
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.TestDocuments
import io.lukia.core.domain.toAppError
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.features.library.domain.LibraryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ImportDocumentUseCaseTest {

    @Test
    fun importsPdfDocument() = runTest {
        val document = TestDocuments.pdf(
            location = "/test/book.pdf",
        )

        val documentInfo = DocumentInfo(
            document = document,
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 1024L,
        )

        val analyzer = FakeDocumentAnalyzer(documentInfo)
        val repository = FakeLibraryRepository()

        val useCase = ImportDocumentUseCase(
            documentAnalyzer = analyzer,
            libraryRepository = repository,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val result = useCase(document)

        assertTrue(result.isSuccess)
        assertEquals(documentInfo, result.getOrThrow())
        assertEquals(listOf(documentInfo), repository.documents)
    }

    @Test
    fun importsEpubDocument() = runTest {
        val document = TestDocuments.epub(
            location = "/test/book.epub",
        )

        val documentInfo = DocumentInfo(
            document = document,
            type = DocumentType.EPUB,
            exists = true,
            sizeBytes = 2048L,
        )

        val analyzer = FakeDocumentAnalyzer(documentInfo)
        val repository = FakeLibraryRepository()

        val useCase = ImportDocumentUseCase(
            documentAnalyzer = analyzer,
            libraryRepository = repository,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val result = useCase(document)

        assertTrue(result.isSuccess)
        assertEquals(documentInfo, result.getOrThrow())
        assertEquals(listOf(documentInfo), repository.documents)
    }

    @Test
    fun rejectsUnknownDocumentType() = runTest {
        val document = TestDocuments.unsupported(
            location = "/test/book.mobi",
        )

        val documentInfo = DocumentInfo(
            document = document,
            type = DocumentType.UNKNOWN,
            exists = true,
            sizeBytes = 512L,
        )

        val analyzer = FakeDocumentAnalyzer(documentInfo)
        val repository = FakeLibraryRepository()

        val useCase = ImportDocumentUseCase(
            documentAnalyzer = analyzer,
            libraryRepository = repository,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val result = useCase(document)

        assertFalse(result.isSuccess)
        assertEquals(
            AppError.UnsupportedFileType,
            result.exceptionOrNull()?.toAppError(),
        )
        assertTrue(repository.documents.isEmpty())
    }

    @Test
    fun analyzerFailureIsPropagated() = runTest {
        val document = TestDocuments.pdf(
            location = "/test/book.pdf",
        )

        val expectedException = IllegalArgumentException(
            "File does not exist",
        )

        val analyzer = FailingDocumentAnalyzer(expectedException)
        val repository = FakeLibraryRepository()

        val useCase = ImportDocumentUseCase(
            documentAnalyzer = analyzer,
            libraryRepository = repository,
            dispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        val exception = try {
            useCase(document)
            null
        } catch (e: Exception) {
            e
        }

        // Asserted by type and message rather than instance: coroutines'
        // stacktrace recovery hands back a copy of the original exception once
        // it crosses the withContext boundary inside the use case.
        assertIs<IllegalArgumentException>(exception)
        assertEquals(expectedException.message, exception.message)
        assertTrue(repository.documents.isEmpty())
    }

    private class FakeDocumentAnalyzer(
        private val documentInfo: DocumentInfo,
    ) : DocumentAnalyzer {

        override suspend fun analyze(
            document: PickedDocument,
        ): DocumentInfo = documentInfo
    }

    private class FailingDocumentAnalyzer(
        private val exception: Exception,
    ) : DocumentAnalyzer {

        override suspend fun analyze(
            document: PickedDocument,
        ): DocumentInfo = throw exception
    }

    private class FakeLibraryRepository : LibraryRepository {

        val documents = mutableListOf<DocumentInfo>()

        override suspend fun addDocument(document: DocumentInfo) {
            documents.add(document)
        }

        override suspend fun getDocuments(): List<DocumentInfo> = documents.toList()

        override suspend fun getDocument(location: String): DocumentInfo? = documents.firstOrNull {
            it.document.location == location
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
}
