package io.lukia.core.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The one place that decides what the library understands.
 *
 * [DocumentType.supportedExtensions] and [DocumentType.fromExtension] answer
 * from the same table, and these say so - a listed extension the library would
 * refuse is a file dialog offering a reader something the import then rejects.
 */
class DocumentTypeTest {

    @Test
    fun `every listed extension is one the library reads`() {
        DocumentType.supportedExtensions.forEach { extension ->
            assertNotEquals(
                DocumentType.UNKNOWN,
                DocumentType.fromExtension(extension),
                "$extension is offered but not understood",
            )
        }
    }

    @Test
    fun `every type the library reads is offered by at least one extension`() {
        val offered = DocumentType.supportedExtensions
            .map(DocumentType::fromExtension)
            .toSet()

        DocumentType.entries
            .filter { it != DocumentType.UNKNOWN }
            .forEach { type ->
                assertTrue(type in offered, "$type is readable but no extension offers it")
            }
    }

    @Test
    fun `an extension is recognised whatever its case`() {
        assertEquals(DocumentType.PDF, DocumentType.fromExtension("PDF"))
        assertEquals(DocumentType.MARKDOWN, DocumentType.fromExtension("Markdown"))
    }

    @Test
    fun `anything else is unknown`() {
        assertEquals(DocumentType.UNKNOWN, DocumentType.fromExtension("mobi"))
        assertEquals(DocumentType.UNKNOWN, DocumentType.fromExtension(""))
    }
}
