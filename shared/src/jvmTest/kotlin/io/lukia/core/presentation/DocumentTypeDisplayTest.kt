package io.lukia.core.presentation

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.shared.composed
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Held apart from `DocumentDisplayTest`, which stays in `commonTest`: naming a
 * format reads a string resource and therefore needs a composition, and the
 * Compose test runtime only reaches the classpath here.
 */
class DocumentTypeDisplayTest {

    @Test
    fun displayTypeMapsKnownTypes() {
        assertEquals("PDF", composed { ofType(DocumentType.PDF).displayType() })
        assertEquals("EPUB", composed { ofType(DocumentType.EPUB).displayType() })
    }

    /**
     * Named for what a reader would call the thing rather than for its
     * extension: "Word" is what the file is, where "DOCX" is how it is stored.
     */
    @Test
    fun displayTypeNamesTheWordProcessedFormats() {
        assertEquals("Word", composed { ofType(DocumentType.DOCX).displayType() })
        assertEquals("Text", composed { ofType(DocumentType.TEXT).displayType() })
        assertEquals("Markdown", composed { ofType(DocumentType.MARKDOWN).displayType() })
    }

    private fun ofType(type: DocumentType) = DocumentInfo(
        document = PickedDocument(
            fileName = "book.bin",
            extension = "bin",
            location = "/test/book.bin",
        ),
        type = type,
        exists = true,
        sizeBytes = 1024L,
    )
}
