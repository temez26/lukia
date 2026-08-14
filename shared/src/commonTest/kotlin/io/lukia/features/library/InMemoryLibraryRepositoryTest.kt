package io.lukia.features.library

import io.lukia.core.domain.TestDocuments
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.features.library.infrastructure.InMemoryLibraryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryLibraryRepositoryTest {

    @Test
    fun startsEmpty() = runTest {
        val repository = InMemoryLibraryRepository()

        assertTrue(repository.getDocuments().isEmpty())
    }

    @Test
    fun addedDocumentsArePersistedInOrder() = runTest {
        val repository = InMemoryLibraryRepository()

        val pdf = DocumentInfo(
            document = TestDocuments.pdf(location = "/test/book.pdf"),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 1024L
        )
        val epub = DocumentInfo(
            document = TestDocuments.epub(location = "/test/book.epub"),
            type = DocumentType.EPUB,
            exists = true,
            sizeBytes = 2048L
        )

        repository.addDocument(pdf)
        repository.addDocument(epub)

        assertEquals(listOf(pdf, epub), repository.getDocuments())
    }

    @Test
    fun getDocumentReturnsMatchingDocumentByLocation() = runTest {
        val repository = InMemoryLibraryRepository()

        val pdf = DocumentInfo(
            document = TestDocuments.pdf(location = "/test/book.pdf"),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 1024L
        )
        val epub = DocumentInfo(
            document = TestDocuments.epub(location = "/test/book.epub"),
            type = DocumentType.EPUB,
            exists = true,
            sizeBytes = 2048L
        )

        repository.addDocument(pdf)
        repository.addDocument(epub)

        assertEquals(epub, repository.getDocument("/test/book.epub"))
    }

    @Test
    fun getDocumentReturnsNullWhenLocationIsUnknown() = runTest {
        val repository = InMemoryLibraryRepository()

        assertNull(repository.getDocument("/test/missing.pdf"))
    }
}
