package io.lukia.features.library

import io.lukia.core.domain.document.CoverImage
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.TestDocuments
import io.lukia.features.library.application.GetLibraryDocumentsUseCase
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
            sizeBytes = 1024L,
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

        override suspend fun getDocuments(): List<DocumentInfo> =
            documents.map { it.copy(coverImage = null) }

        override suspend fun getDocument(location: String): DocumentInfo? = documents.firstOrNull {
            it.document.location == location
        }

        /** Read by location, exactly as the real store reads it. */
        override suspend fun coverFor(location: String): CoverImage? =
            getDocument(location)?.coverImage

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
