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
    fun addingSameLocationTwiceReplacesTheExistingDocument() = runTest {
        val repository = InMemoryLibraryRepository()

        val original = DocumentInfo(
            document = TestDocuments.pdf(location = "/test/book.pdf"),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 1024L
        )
        val reimported = original.copy(sizeBytes = 4096L)

        repository.addDocument(original)
        repository.addDocument(reimported)

        assertEquals(listOf(reimported), repository.getDocuments())
        assertEquals(reimported, repository.getDocument("/test/book.pdf"))
    }

    @Test
    fun replacedDocumentKeepsItsPositionInTheLibrary() = runTest {
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
        val reimportedPdf = pdf.copy(sizeBytes = 4096L)

        repository.addDocument(pdf)
        repository.addDocument(epub)
        repository.addDocument(reimportedPdf)

        assertEquals(listOf(reimportedPdf, epub), repository.getDocuments())
    }

    @Test
    fun getDocumentReturnsNullWhenLocationIsUnknown() = runTest {
        val repository = InMemoryLibraryRepository()

        assertNull(repository.getDocument("/test/missing.pdf"))
    }

    @Test
    fun setFavoriteFlipsTheFlagOnTheStoredDocument() = runTest {
        val repository = InMemoryLibraryRepository()
        val pdf = pdfDocument()
        repository.addDocument(pdf)

        repository.setFavorite("/test/book.pdf", true)

        assertEquals(true, repository.getDocument("/test/book.pdf")?.isFavorite)

        repository.setFavorite("/test/book.pdf", false)

        assertEquals(false, repository.getDocument("/test/book.pdf")?.isFavorite)
    }

    @Test
    fun setFavoriteOnUnknownLocationIsANoOp() = runTest {
        val repository = InMemoryLibraryRepository()

        repository.setFavorite("/test/missing.pdf", true)

        assertTrue(repository.getDocuments().isEmpty())
    }

    /**
     * A folder rescan re-imports every file it finds, and a freshly analyzed
     * document is never favourited - so without this the next scan quietly
     * clears every heart in the library.
     */
    @Test
    fun addingSameLocationPreservesAnExistingFavourite() = runTest {
        val repository = InMemoryLibraryRepository()
        val pdf = pdfDocument()
        repository.addDocument(pdf)
        repository.setFavorite("/test/book.pdf", true)

        repository.addDocument(pdf.copy(sizeBytes = 4096L))

        val stored = repository.getDocument("/test/book.pdf")
        assertEquals(true, stored?.isFavorite)
        assertEquals(4096L, stored?.sizeBytes)
    }

    @Test
    fun removeDocumentDropsTheEntry() = runTest {
        val repository = InMemoryLibraryRepository()
        val pdf = pdfDocument()
        val epub = DocumentInfo(
            document = TestDocuments.epub(location = "/test/book.epub"),
            type = DocumentType.EPUB,
            exists = true,
            sizeBytes = 2048L
        )
        repository.addDocument(pdf)
        repository.addDocument(epub)

        repository.removeDocument("/test/book.pdf")

        assertEquals(listOf(epub), repository.getDocuments())
        assertNull(repository.getDocument("/test/book.pdf"))
    }

    @Test
    fun removeDocumentOnUnknownLocationIsANoOp() = runTest {
        val repository = InMemoryLibraryRepository()
        val pdf = pdfDocument()
        repository.addDocument(pdf)

        repository.removeDocument("/test/missing.pdf")

        assertEquals(listOf(pdf), repository.getDocuments())
    }

    private fun pdfDocument() = DocumentInfo(
        document = TestDocuments.pdf(location = "/test/book.pdf"),
        type = DocumentType.PDF,
        exists = true,
        sizeBytes = 1024L
    )
}
