package io.lukia.features.library

import io.lukia.features.import.TestDocuments
import io.lukia.features.import.domain.DocumentInfo
import io.lukia.features.import.domain.DocumentType
import io.lukia.features.library.domain.GetLibraryDocumentsUseCase
import io.lukia.features.library.domain.LibraryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetLibraryDocumentsUseCaseTest {

    @Test
    fun returnsEmptyListWhenNoDocumentsStored() = runTest {
        val useCase = GetLibraryDocumentsUseCase(FakeLibraryRepository())

        assertTrue(useCase().isEmpty())
    }

    @Test
    fun returnsDocumentsFromRepository() = runTest {
        val documentInfo = DocumentInfo(
            document = TestDocuments.pdf(location = "/test/book.pdf"),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 1024L
        )
        val repository = FakeLibraryRepository().apply {
            addDocument(documentInfo)
        }

        val useCase = GetLibraryDocumentsUseCase(repository)

        assertEquals(listOf(documentInfo), useCase())
    }

    private class FakeLibraryRepository : LibraryRepository {
        private val documents = mutableListOf<DocumentInfo>()

        override suspend fun addDocument(document: DocumentInfo) {
            documents.add(document)
        }

        override suspend fun getDocuments(): List<DocumentInfo> {
            return documents.toList()
        }
    }
}
