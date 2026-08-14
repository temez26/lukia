package io.lukia.features.library

import io.lukia.core.domain.TestDocuments
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.features.library.presentation.displayTitle
import io.lukia.features.library.presentation.displayType
import io.lukia.features.library.presentation.formatFileSize
import io.lukia.features.library.presentation.formattedSize
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryBookPresentationTest {

    @Test
    fun displayTitleStripsExtension() {
        val documentInfo = DocumentInfo(
            document = TestDocuments.pdf(location = "/test/book.pdf"),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 1024L
        )

        assertEquals("book", documentInfo.displayTitle())
    }

    @Test
    fun displayTitleFallsBackToFileNameWhenExtensionIsBlank() {
        val documentInfo = DocumentInfo(
            document = io.lukia.core.domain.PickedDocument(
                fileName = "book",
                extension = "",
                location = "/test/book"
            ),
            type = DocumentType.UNKNOWN,
            exists = true,
            sizeBytes = 0L
        )

        assertEquals("book", documentInfo.displayTitle())
    }

    @Test
    fun displayTypeMapsKnownTypes() {
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
            sizeBytes = 1024L
        )

        assertEquals("PDF", pdf.displayType())
        assertEquals("EPUB", epub.displayType())
    }

    @Test
    fun formattedSizeDelegatesToFormatFileSize() {
        val documentInfo = DocumentInfo(
            document = TestDocuments.pdf(location = "/test/book.pdf"),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 2048L
        )

        assertEquals(formatFileSize(2048L), documentInfo.formattedSize())
    }

    @Test
    fun formatFileSizeUsesBytesBelowOneKilobyte() {
        assertEquals("512 B", formatFileSize(512L))
    }

    @Test
    fun formatFileSizeUsesKilobytes() {
        assertEquals("1.5 KB", formatFileSize(1536L))
    }

    @Test
    fun formatFileSizeUsesMegabytes() {
        assertEquals("2 MB", formatFileSize(2 * 1024 * 1024L))
    }

    @Test
    fun formatFileSizeUsesGigabytes() {
        assertEquals("1 GB", formatFileSize(1024L * 1024L * 1024L))
    }

    @Test
    fun formatFileSizeHandlesZero() {
        assertEquals("0 B", formatFileSize(0L))
    }
}
