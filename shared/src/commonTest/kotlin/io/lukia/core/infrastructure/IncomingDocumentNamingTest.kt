package io.lukia.core.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IncomingDocumentNamingTest {

    @Test
    fun theSameBookSharedTwiceLandsOnTheSameName() {
        val bytes = "a book".encodeToByteArray()

        assertEquals(
            IncomingDocumentNaming.fileName(bytes, "pdf"),
            IncomingDocumentNaming.fileName("a book".encodeToByteArray(), "pdf"),
        )
    }

    @Test
    fun twoBooksSentUnderOneFileNameDoNotCollide() {
        // Both arrive as document.pdf, which is why the name is not hashed.
        val first = IncomingDocumentNaming.fileName("first".encodeToByteArray(), "pdf")
        val second = IncomingDocumentNaming.fileName("second".encodeToByteArray(), "pdf")

        assertNotEquals(first, second)
    }

    @Test
    fun theExtensionIsKeptLowercasedAndStrippedOfAnythingOdd() {
        val name = IncomingDocumentNaming.fileName("book".encodeToByteArray(), "PDF")

        assertTrue(name.endsWith(".pdf"), name)
    }

    @Test
    fun aDocumentWithNoExtensionIsStillGivenAName() {
        val name = IncomingDocumentNaming.fileName("book".encodeToByteArray(), "")

        assertTrue(name.startsWith("in-"), name)
        assertTrue(!name.contains('.'), name)
    }

    @Test
    fun anExtensionThatIsAllPunctuationIsDroppedRatherThanKept() {
        val name = IncomingDocumentNaming.fileName("book".encodeToByteArray(), "../..")

        assertTrue(!name.contains('.'), name)
        assertTrue(!name.contains('/'), name)
    }

    /** A picked book's name never reaches outside the directory, whatever the id holds. */
    @Test
    fun aPickedNameKeepsOnlyTheIdsLettersAndDigits() {
        val name = IncomingDocumentNaming.pickedName("../AB-12", "EPUB")

        assertEquals("pick-ab12.epub", name)
    }
}
