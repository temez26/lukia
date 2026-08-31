package io.lukia.features.library

import io.lukia.core.domain.document.CoverImage
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.TestDocuments
import io.lukia.core.domain.reading.FakePdfLayoutRepository
import io.lukia.core.domain.reading.FakeReadingProgressRepository
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.domain.storage.FakeDocumentWorkspace
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.library.domain.LibraryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RemoveDocumentUseCaseTest {

    private val library = FakeLibraryRepository()
    private val progress = FakeReadingProgressRepository()
    private val workspace = FakeDocumentWorkspace()
    private val layouts = FakePdfLayoutRepository()

    private val removeDocument = RemoveDocumentUseCase(library, workspace, progress, layouts)

    @Test
    fun removingABookTakesItOutOfTheLibrary() = runTest {
        library.addDocument(document())

        removeDocument(LOCATION)

        assertTrue(library.getDocuments().isEmpty())
    }

    /**
     * A position kept for a book that is no longer listed is state with no way
     * to reach it - and the reader would not expect a removed book to remember
     * anything about them.
     */
    @Test
    fun removingABookForgetsWhereItWasLeftOff() = runTest {
        library.addDocument(document())
        progress.save(LOCATION, ReadingProgress(pageIndex = 42, pageCount = 300))

        removeDocument(LOCATION)

        assertNull(progress.progressFor(LOCATION))
    }

    /** Removing a book the library does not have is not an error anywhere. */
    @Test
    fun removingABookThatIsNotThereDoesNothing() = runTest {
        removeDocument(LOCATION)

        assertTrue(library.getDocuments().isEmpty())
        assertNull(progress.progressFor(LOCATION))
    }

    private fun document() = DocumentInfo(
        document = TestDocuments.pdf(location = LOCATION),
        type = DocumentType.PDF,
        exists = true,
        sizeBytes = 1024L,
    )

    /** Private to this test, as the other use-case tests here keep theirs. */
    private class FakeLibraryRepository : LibraryRepository {

        private val documents = mutableMapOf<String, DocumentInfo>()

        override suspend fun addDocument(document: DocumentInfo) {
            documents[document.document.location] = document
        }

        override suspend fun getDocuments(): List<DocumentInfo> =
            documents.values.map { it.copy(coverImage = null) }

        override suspend fun getDocument(location: String): DocumentInfo? = documents[location]

        /** Read by location, exactly as the real store reads it. */
        override suspend fun coverFor(location: String): CoverImage? =
            getDocument(location)?.coverImage

        /** Answered the expensive way; nothing in a test cares what it costs. */
        override suspend fun knownLocations(): Set<String> =
            getDocuments().mapTo(mutableSetOf()) { it.document.location }

        override suspend fun setFavorite(location: String, isFavorite: Boolean) {
            documents[location]?.let { documents[location] = it.copy(isFavorite = isFavorite) }
        }

        override suspend fun removeDocument(location: String) {
            documents.remove(location)
        }
    }

    private companion object {
        const val LOCATION = "/library/book.pdf"
    }
}
