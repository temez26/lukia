package io.lukia.features.import.domain

import io.lukia.features.import.TestDocuments
import io.lukia.features.library.domain.LibraryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImportDocumentUseCaseTest {

    @Test
    fun importsPdfDocument() = runTest {
        val document = TestDocuments.pdf(
            location = "/test/book.pdf"
        )

        val documentInfo = DocumentInfo(
            document = document,
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 1024L
        )

        val analyzer = FakeDocumentAnalyzer(documentInfo)
        val repository = FakeLibraryRepository()

        val useCase = ImportDocumentUseCase(
            documentAnalyzer = analyzer,
            libraryRepository = repository
        )

        val result = useCase.execute(document)

        assertTrue(result.isSuccess)
        assertEquals(documentInfo, result.getOrThrow())
        assertEquals(listOf(documentInfo), repository.documents)
    }

    @Test
    fun importsEpubDocument() = runTest {
        val document = TestDocuments.epub(
            location = "/test/book.epub"
        )

        val documentInfo = DocumentInfo(
            document = document,
            type = DocumentType.EPUB,
            exists = true,
            sizeBytes = 2048L
        )

        val analyzer = FakeDocumentAnalyzer(documentInfo)
        val repository = FakeLibraryRepository()

        val useCase = ImportDocumentUseCase(
            documentAnalyzer = analyzer,
            libraryRepository = repository
        )

        val result = useCase.execute(document)

        assertTrue(result.isSuccess)
        assertEquals(documentInfo, result.getOrThrow())
        assertEquals(listOf(documentInfo), repository.documents)
    }

    @Test
    fun rejectsUnknownDocumentType() = runTest {
        val document = TestDocuments.txt(
            location = "/test/book.txt"
        )

        val documentInfo = DocumentInfo(
            document = document,
            type = DocumentType.UNKNOWN,
            exists = true,
            sizeBytes = 512L
        )

        val analyzer = FakeDocumentAnalyzer(documentInfo)
        val repository = FakeLibraryRepository()

        val useCase = ImportDocumentUseCase(
            documentAnalyzer = analyzer,
            libraryRepository = repository
        )

        val result = useCase.execute(document)

        assertFalse(result.isSuccess)
        assertEquals(
            "Unsupported file type",
            result.exceptionOrNull()?.message
        )
        assertTrue(repository.documents.isEmpty())
    }

    @Test
    fun analyzerFailureIsPropagated() = runTest {
        val document = TestDocuments.pdf(
            location = "/test/book.pdf"
        )

        val expectedException = IllegalArgumentException(
            "File does not exist"
        )

        val analyzer = FailingDocumentAnalyzer(expectedException)
        val repository = FakeLibraryRepository()

        val useCase = ImportDocumentUseCase(
            documentAnalyzer = analyzer,
            libraryRepository = repository
        )

        val exception = try {
            useCase.execute(document)
            null
        } catch (e: Exception) {
            e
        }

        assertEquals(expectedException, exception)
        assertTrue(repository.documents.isEmpty())
    }

    private class FakeDocumentAnalyzer(
        private val documentInfo: DocumentInfo
    ) : DocumentAnalyzer {

        override suspend fun analyze(
            document: PickedDocument
        ): DocumentInfo {
            return documentInfo
        }
    }

    private class FailingDocumentAnalyzer(
        private val exception: Exception
    ) : DocumentAnalyzer {

        override suspend fun analyze(
            document: PickedDocument
        ): DocumentInfo {
            throw exception
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
    }
}