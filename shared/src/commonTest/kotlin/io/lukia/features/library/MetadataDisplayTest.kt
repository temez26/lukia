package io.lukia.features.library

import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.presentation.bookTitle
import io.lukia.core.presentation.publishedYear
import kotlin.test.Test
import kotlin.test.assertEquals

class MetadataDisplayTest {

    private fun book(
        fileName: String = "moby-dick.epub",
        type: DocumentType = DocumentType.EPUB,
        metadata: BookMetadata? = null,
    ) = DocumentInfo(
        document = PickedDocument(
            fileName = fileName,
            extension = fileName.substringAfterLast('.'),
            location = "/books/$fileName",
        ),
        type = type,
        exists = true,
        sizeBytes = 1024L,
        metadata = metadata,
    )

    /** A book that names itself names itself better than the file name does. */
    @Test
    fun prefersTheTitleTheDocumentDeclares() {
        val info = book(metadata = BookMetadata(title = "Moby Dick; or, The Whale"))

        assertEquals("Moby Dick; or, The Whale", info.bookTitle())
    }

    @Test
    fun fallsBackToTheFileNameWithoutItsExtension() {
        assertEquals("moby-dick", book().bookTitle())
    }

    @Test
    fun aBlankDeclaredTitleIsNoTitle() {
        assertEquals("moby-dick", book(metadata = BookMetadata(title = "  ")).bookTitle())
    }

    @Test
    fun takesTheYearOutOfAFullDate() {
        assertEquals("1851", BookMetadata(published = "1851-11-14T00:00:00Z").publishedYear())
    }

    @Test
    fun leavesABareYearAlone() {
        assertEquals("2018", BookMetadata(published = "2018").publishedYear())
    }

    /** A book that says "Spring 1851" is telling the truth in its own way. */
    @Test
    fun passesThroughSomethingThatIsNotAYearFirst() {
        assertEquals("Spring 1851", BookMetadata(published = "Spring 1851").publishedYear())
    }
}
