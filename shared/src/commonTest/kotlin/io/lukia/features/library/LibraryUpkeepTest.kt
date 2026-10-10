package io.lukia.features.library

import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.features.library.presentation.model.asLibrary
import io.lukia.features.library.presentation.model.upkeep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What is worth tidying on a shelf nobody has looked after in a while.
 */
class LibraryUpkeepTest {

    private fun book(
        title: String,
        location: String = "/books/$title.epub",
        size: Long = 1024L,
        exists: Boolean = true,
    ) = DocumentInfo(
        document = PickedDocument(
            fileName = "$title.epub",
            extension = "epub",
            location = location,
        ),
        type = DocumentType.EPUB,
        exists = exists,
        sizeBytes = size,
        metadata = BookMetadata(title = title),
    )

    private fun shelf(vararg books: DocumentInfo) = books.toList().asLibrary(emptyMap())

    @Test
    fun aTidyShelfNeedsNothingDoing() {
        val found = shelf(book("One"), book("Two")).upkeep()

        assertTrue(found.missing.isEmpty())
        assertTrue(found.duplicates.isEmpty())
    }

    @Test
    fun aBookWhoseFileHasGoneIsMissing() {
        val gone = book("Gone", exists = false)

        val found = shelf(book("Here"), gone).upkeep()

        assertEquals(setOf(gone.document.location), found.missing)
    }

    /**
     * Never the first: acting on every duplicate has to leave exactly one of
     * each book behind, or tidying a shelf would empty it.
     */
    @Test
    fun theSecondCopyIsTheDuplicateAndTheFirstIsTheBook() {
        val original = book("Twice", location = "/books/twice.epub")
        val copy = book("Twice", location = "/downloads/twice.epub")

        val found = shelf(original, copy).upkeep()

        assertEquals(setOf(copy.document.location), found.duplicates)
    }

    @Test
    fun everyCopyPastTheFirstIsADuplicate() {
        val found = shelf(
            book("Thrice", location = "/a.epub"),
            book("Thrice", location = "/b.epub"),
            book("Thrice", location = "/c.epub"),
        ).upkeep()

        assertEquals(setOf("/b.epub", "/c.epub"), found.duplicates)
    }

    /** A library holds several books called Poems, and they are different books. */
    @Test
    fun booksThatShareOnlyATitleAreNotCopiesOfEachOther() {
        val found = shelf(
            book("Poems", location = "/a.epub", size = 1000L),
            book("Poems", location = "/b.epub", size = 2000L),
        ).upkeep()

        assertTrue(found.duplicates.isEmpty())
    }

    /** And a great many files happen to be the same number of bytes. */
    @Test
    fun booksThatShareOnlyASizeAreNotCopiesOfEachOther() {
        val found = shelf(
            book("One", location = "/a.epub", size = 1024L),
            book("Another", location = "/b.epub", size = 1024L),
        ).upkeep()

        assertTrue(found.duplicates.isEmpty())
    }

    /** A book can be both, and each answer is about a different thing to do. */
    @Test
    fun aMissingDuplicateIsCountedInBoth() {
        val copy = book("Twice", location = "/b.epub", exists = false)

        val found = shelf(book("Twice", location = "/a.epub"), copy).upkeep()

        assertEquals(setOf("/b.epub"), found.missing)
        assertEquals(setOf("/b.epub"), found.duplicates)
    }

    /**
     * The same book downloaded twice is routinely `dracula.epub` and
     * `dracula (1).epub` - the file names never match, and the declared title
     * always does.
     */
    @Test
    fun theSameBookUnderTwoFileNamesIsStillACopy() {
        val original = book("Dracula", location = "/books/dracula.epub")
        val again = book("Dracula", location = "/books/dracula (1).epub").let {
            it.copy(document = it.document.copy(fileName = "dracula (1).epub"))
        }

        val found = shelf(original, again).upkeep()

        assertEquals(setOf(again.document.location), found.duplicates)
    }
}
