package io.lukia.core.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IncomingDocumentNamingTest {

    @Test
    fun `the same book shared twice lands on the same name`() {
        val bytes = "a book".encodeToByteArray()

        assertEquals(
            IncomingDocumentNaming.fileName(bytes, "pdf"),
            IncomingDocumentNaming.fileName("a book".encodeToByteArray(), "pdf"),
        )
    }

    @Test
    fun `two books sent under one file name do not collide`() {
        // Both arrive as document.pdf, which is why the name is not hashed.
        val first = IncomingDocumentNaming.fileName("first".encodeToByteArray(), "pdf")
        val second = IncomingDocumentNaming.fileName("second".encodeToByteArray(), "pdf")

        assertNotEquals(first, second)
    }

    @Test
    fun `the extension is kept, lowercased and stripped of anything odd`() {
        val name = IncomingDocumentNaming.fileName("book".encodeToByteArray(), "PDF")

        assertTrue(name.endsWith(".pdf"), name)
    }

    @Test
    fun `a document with no extension is still given a name`() {
        val name = IncomingDocumentNaming.fileName("book".encodeToByteArray(), "")

        assertTrue(name.startsWith("in-"), name)
        assertTrue(!name.contains('.'), name)
    }

    @Test
    fun `an extension that is all punctuation is dropped rather than kept`() {
        val name = IncomingDocumentNaming.fileName("book".encodeToByteArray(), "../..")

        assertTrue(!name.contains('.'), name)
        assertTrue(!name.contains('/'), name)
    }
}
