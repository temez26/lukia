package io.lukia.features.bookdetail

import io.lukia.features.bookdetail.application.GetDocumentDetailsUseCase
import io.lukia.core.domain.TestDocuments
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.reader.domain.FakeReadingProgressRepository
import io.lukia.features.reader.domain.ReadingProgress
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GetDocumentDetailsUseCaseTest {

    private val documentInfo = DocumentInfo(
        document = TestDocuments.pdf(location = "/test/book.pdf"),
        type = DocumentType.PDF,
        exists = true,
        sizeBytes = 1024L
    )

    @Test
    fun returnsDocumentMatchingLocation() = runTest {
        val useCase = useCase(documents = listOf(documentInfo))

        assertEquals(documentInfo, useCase("/test/book.pdf")?.document)
    }

    @Test
    fun returnsNullWhenNoDocumentMatchesLocation() = runTest {
        assertNull(useCase()("/test/missing.pdf"))
    }

    /** How far through a book someone is comes from a store the library never reads. */
    @Test
    fun carriesTheStoredReadingPosition() = runTest {
        val progress = ReadingProgress(pageIndex = 41, pageCount = 100)
        val useCase = useCase(
            documents = listOf(documentInfo),
            positions = mapOf("/test/book.pdf" to progress)
        )

        val details = useCase("/test/book.pdf")

        assertEquals(progress, details?.progress)
        assertEquals(42, details?.percentRead)
    }

    /**
     * A book nobody has opened has no position, which is not the same as being
     * on page one - the screen says "Not started" rather than "1%".
     */
    @Test
    fun aBookNeverOpenedHasNoProgress() = runTest {
        val details = useCase(documents = listOf(documentInfo))("/test/book.pdf")

        assertNull(details?.progress)
        assertNull(details?.percentRead)
    }

    /**
     * A reading position may outlive the library entry it belongs to - the
     * schema deliberately does not tie the two together - but this screen shows
     * books, so a position with no book behind it is still nothing to show.
     */
    @Test
    fun aPositionWithNoLibraryEntryIsStillNothing() = runTest {
        val useCase = useCase(
            positions = mapOf("/test/book.pdf" to ReadingProgress(pageIndex = 3, pageCount = 10))
        )

        assertNull(useCase("/test/book.pdf"))
    }

    private fun useCase(
        documents: List<DocumentInfo> = emptyList(),
        positions: Map<String, ReadingProgress> = emptyMap()
    ) = GetDocumentDetailsUseCase(
        libraryRepository = FakeLibraryRepository(documents),
        readingProgressRepository = FakeReadingProgressRepository(positions.toMutableMap())
    )

    private class FakeLibraryRepository(
        initial: List<DocumentInfo> = emptyList()
    ) : LibraryRepository {
        private val documents = initial.toMutableList()

        override suspend fun addDocument(document: DocumentInfo) {
            documents.add(document)
        }

        override suspend fun getDocuments(): List<DocumentInfo> {
            return documents.toList()
        }

        override suspend fun getDocument(location: String): DocumentInfo? {
            return documents.firstOrNull { it.document.location == location }
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
