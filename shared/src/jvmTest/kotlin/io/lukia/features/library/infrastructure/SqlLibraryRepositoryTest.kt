package io.lukia.features.library.infrastructure

import io.lukia.core.domain.CoverImage
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.testDatabase
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlLibraryRepositoryTest {

    private val database: LukiaDatabase = testDatabase()

    private val repository = SqlLibraryRepository(
        database = database,
        dispatcher = UnconfinedTestDispatcher()
    )

    @Test
    fun aStoredBookComesBackWhole() = runTest {
        repository.addDocument(document("/books/one.pdf", cover = byteArrayOf(1, 2, 3)))

        val stored = repository.getDocument("/books/one.pdf")

        assertEquals("one.pdf", stored?.document?.fileName)
        assertEquals("pdf", stored?.document?.extension)
        assertEquals(DocumentType.PDF, stored?.type)
        assertEquals(1024L, stored?.sizeBytes)
        assertEquals(true, stored?.exists)
        assertContentEquals(byteArrayOf(1, 2, 3), stored?.coverImage?.bytes)
    }

    /** The whole point of a second run: a library that is still there. */
    @Test
    fun booksSurviveTheRepositoryThatStoredThem() = runTest {
        repository.addDocument(document("/books/one.pdf"))
        repository.addDocument(document("/books/two.pdf"))

        val reopened = SqlLibraryRepository(database, UnconfinedTestDispatcher())

        assertEquals(
            listOf("/books/one.pdf", "/books/two.pdf"),
            reopened.getDocuments().map { it.document.location }
        )
    }

    @Test
    fun aLibraryNothingHasBeenAddedToIsEmpty() = runTest {
        assertTrue(repository.getDocuments().isEmpty())
        assertNull(repository.getDocument("/books/never-imported.pdf"))
    }

    @Test
    fun aBookImportedTwiceIsOneBook() = runTest {
        repository.addDocument(document("/books/one.pdf"))
        repository.addDocument(document("/books/one.pdf"))

        assertEquals(1, repository.getDocuments().size)
    }

    /**
     * Re-importing a book, or rescanning the folder it lives in, analyses it
     * again - and the analyser has no idea it was ever a favourite.
     */
    @Test
    fun reimportingKeepsWhatOnlyTheReaderKnows() = runTest {
        repository.addDocument(document("/books/one.pdf"))
        repository.setFavorite("/books/one.pdf", true)

        repository.addDocument(document("/books/one.pdf", size = 2048L))

        val stored = repository.getDocument("/books/one.pdf")
        assertEquals(true, stored?.isFavorite)
        assertEquals(2048L, stored?.sizeBytes, "the file's own facts are refreshed")
    }

    /** A book keeps its place on the shelf when it is refreshed. */
    @Test
    fun reimportingDoesNotMoveABookToTheEndOfTheLibrary() = runTest {
        repository.addDocument(document("/books/one.pdf"))
        repository.addDocument(document("/books/two.pdf"))
        repository.addDocument(document("/books/three.pdf"))

        repository.addDocument(document("/books/one.pdf"))

        assertEquals(
            listOf("/books/one.pdf", "/books/two.pdf", "/books/three.pdf"),
            repository.getDocuments().map { it.document.location }
        )
    }

    /**
     * A cover is rendered from the file and can fail on a second reading - a
     * locked file, a page that would not draw. Losing the artwork already
     * stored over it would be a visible loss for no gain.
     */
    @Test
    fun aRefreshWithoutACoverKeepsTheOneAlreadyStored() = runTest {
        repository.addDocument(document("/books/one.pdf", cover = byteArrayOf(9, 9)))

        repository.addDocument(document("/books/one.pdf", cover = null))

        assertContentEquals(
            byteArrayOf(9, 9),
            repository.getDocument("/books/one.pdf")?.coverImage?.bytes
        )
    }

    @Test
    fun aFavouriteCanBeTakenBack() = runTest {
        repository.addDocument(document("/books/one.pdf"))

        repository.setFavorite("/books/one.pdf", true)
        repository.setFavorite("/books/one.pdf", false)

        assertEquals(false, repository.getDocument("/books/one.pdf")?.isFavorite)
    }

    @Test
    fun removingABookLeavesTheRestOfTheLibrary() = runTest {
        repository.addDocument(document("/books/one.pdf"))
        repository.addDocument(document("/books/two.pdf"))

        repository.removeDocument("/books/one.pdf")

        assertNull(repository.getDocument("/books/one.pdf"))
        assertEquals(1, repository.getDocuments().size)
    }

    /**
     * Both are events from a list that may already have moved on, so there is
     * nothing useful either could do with a failure.
     */
    @Test
    fun favouritingOrRemovingABookThatIsNotThereDoesNothing() = runTest {
        repository.setFavorite("/books/ghost.pdf", true)
        repository.removeDocument("/books/ghost.pdf")

        assertTrue(repository.getDocuments().isEmpty())
    }

    /**
     * The type is stored by name, so an enum that gains a case in the middle
     * does not turn every PDF in a stored library into something else.
     */
    @Test
    fun anUnreadableTypeReadsAsUnknownRatherThanAsTheWrongThing() = runTest {
        repository.addDocument(document("/books/one.pdf"))
        database.documentQueries.refresh(
            fileName = "one.pdf",
            extension = "pdf",
            type = "SOMETHING_THIS_VERSION_HAS_NEVER_HEARD_OF",
            fileExists = true,
            sizeBytes = 1024L,
            coverImage = null,
            hasMetadata = false,
            metaTitle = null,
            metaAuthors = null,
            metaPublisher = null,
            metaPublished = null,
            metaLanguage = null,
            metaDescription = null,
            metaSubjects = null,
            metaIdentifier = null,
            location = "/books/one.pdf"
        )

        assertEquals(DocumentType.UNKNOWN, repository.getDocument("/books/one.pdf")?.type)
    }

    private fun document(
        location: String,
        size: Long = 1024L,
        cover: ByteArray? = null
    ) = DocumentInfo(
        document = PickedDocument(
            fileName = location.substringAfterLast('/'),
            extension = "pdf",
            location = location
        ),
        type = DocumentType.PDF,
        exists = true,
        sizeBytes = size,
        coverImage = cover?.let { CoverImage(it) }
    )
}
