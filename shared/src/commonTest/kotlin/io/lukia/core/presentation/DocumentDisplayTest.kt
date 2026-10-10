package io.lukia.core.presentation

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.document.TestDocuments
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentDisplayTest {

    @Test
    fun displayTitleStripsExtension() {
        val documentInfo = DocumentInfo(
            document = TestDocuments.pdf(location = "/test/book.pdf"),
            type = DocumentType.PDF,
            exists = true,
            sizeBytes = 1024L,
        )

        assertEquals("book", documentInfo.displayTitle())
    }

    @Test
    fun displayTitleFallsBackToFileNameWhenExtensionIsBlank() {
        val documentInfo = DocumentInfo(
            document = PickedDocument(
                fileName = "book",
                extension = "",
                location = "/test/book",
            ),
            type = DocumentType.UNKNOWN,
            exists = true,
            sizeBytes = 0L,
        )

        assertEquals("book", documentInfo.displayTitle())
    }
}
