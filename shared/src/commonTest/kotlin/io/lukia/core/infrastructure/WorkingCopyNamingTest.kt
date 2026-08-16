package io.lukia.core.infrastructure

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class WorkingCopyNamingTest {

    @Test
    fun theSameLocationAlwaysGetsTheSameName() {
        val location = "/storage/emulated/0/Download/book.pdf"

        assertEquals(
            WorkingCopyNaming.fileName(location, "pdf"),
            WorkingCopyNaming.fileName(location, "pdf")
        )
    }

    @Test
    fun differentLocationsGetDifferentNames() {
        assertNotEquals(
            WorkingCopyNaming.fileName("/books/one.pdf", "pdf"),
            WorkingCopyNaming.fileName("/books/two.pdf", "pdf")
        )
    }

    /**
     * Two files one character apart is the case a weak hash gets wrong, and a
     * library is full of them.
     */
    @Test
    fun nearIdenticalLocationsGetDifferentNames() {
        assertNotEquals(
            WorkingCopyNaming.fileName("/books/chapter-01.pdf", "pdf"),
            WorkingCopyNaming.fileName("/books/chapter-02.pdf", "pdf")
        )
    }

    @Test
    fun theNameKeepsTheOriginalExtension() {
        assertTrue(WorkingCopyNaming.fileName("/books/one.pdf", "pdf").endsWith(".pdf"))
        assertTrue(WorkingCopyNaming.fileName("/books/one.epub", "epub").endsWith(".epub"))
    }

    @Test
    fun anExtensionIsNormalisedToLowercase() {
        assertTrue(WorkingCopyNaming.fileName("/books/one.PDF", "PDF").endsWith(".pdf"))
    }

    /**
     * A location arrives as a path, a URI or a URL, and none of what separates
     * their parts may end up in a file name.
     */
    @Test
    fun noLocationSeparatorSurvivesIntoTheName() {
        val locations = listOf(
            "/storage/emulated/0/Download/book.pdf",
            "content://com.android.providers.downloads.documents/document/42",
            "file:///Users/test/Documents/book.pdf",
            "C:\\Users\\test\\Documents\\book.pdf"
        )

        locations.forEach { location ->
            val name = WorkingCopyNaming.fileName(location, "pdf")

            assertTrue(name.none { it in "/\\:?*\"<>|" }, "unusable file name: $name")
        }
    }

    @Test
    fun aDocumentWithNoExtensionStillGetsAName() {
        val name = WorkingCopyNaming.fileName("content://documents/42", "")

        assertTrue(name.isNotEmpty())
        assertTrue(!name.endsWith("."))
    }

    /**
     * Discarding a copy knows only the location it came from, so the prefix has
     * to be the part of the name the extension is appended to.
     */
    @Test
    fun theNameStartsWithThePrefixForItsLocation() {
        val location = "/books/one.pdf"

        assertTrue(
            WorkingCopyNaming.fileName(location, "pdf")
                .startsWith(WorkingCopyNaming.prefix(location))
        )
    }
}
