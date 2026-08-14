package io.lukia.features.bookdetail

import io.lukia.features.bookdetail.application.GetDocumentDetailsUseCase
import io.lukia.core.domain.TestDocuments
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.features.library.domain.LibraryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetDocumentDetailsUseCaseTest {

    @Test
    fun returnsDocumentMatchingLocation() = runTest {
        val documentInfo = DocumentInfo(
            document = TestDocuments.pdf(location = "/test/book.pdf"),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 1024L
        )
        val repository = FakeLibraryRepository().apply {
            addDocument(documentInfo)
        }

        val useCase = GetDocumentDetailsUseCase(repository)

        assertEquals(documentInfo, useCase("/test/book.pdf"))
    }

    @Test
    fun returnsNullWhenNoDocumentMatchesLocation() = runTest {
        val useCase = GetDocumentDetailsUseCase(FakeLibraryRepository())

        assertNull(useCase("/test/missing.pdf"))
    }

    private class FakeLibraryRepository : LibraryRepository {
        private val documents = mutableListOf<DocumentInfo>()

        override suspend fun addDocument(document: DocumentInfo) {
            documents.add(document)
        }

        override suspend fun getDocuments(): List<DocumentInfo> {
            return documents.toList()
        }

        override suspend fun getDocument(location: String): DocumentInfo? {
            return documents.firstOrNull { it.document.location == location }
        }
    }
}
