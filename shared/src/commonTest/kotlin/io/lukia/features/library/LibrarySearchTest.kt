package io.lukia.features.library

import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.features.library.presentation.matchingQuery
import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySearchTest {

    private fun document(title: String) = DocumentInfo(
        document = PickedDocument(
            fileName = "$title.pdf",
            extension = "pdf",
            location = "/library/$title.pdf"
        ),
        type = DocumentType.PDF,
        exists = true,
        sizeBytes = 1024L
    )

    private val library = listOf(
        document("Moby Dick"),
        document("The Hobbit"),
        document("dune")
    )

    @Test
    fun blankQueryMatchesEverything() {
        assertEquals(library, library.matchingQuery(""))
        assertEquals(library, library.matchingQuery("   "))
    }

    @Test
    fun matchesOnAnyPartOfTheTitle() {
        assertEquals(
            listOf("The Hobbit"),
            library.matchingQuery("hobb").map { it.document.fileName.removeSuffix(".pdf") }
        )
    }

    @Test
    fun matchingIgnoresCase() {
        assertEquals(1, library.matchingQuery("DUNE").size)
        assertEquals(1, library.matchingQuery("moby").size)
    }

    /** Trailing spaces come free with typing, and should not empty the library. */
    @Test
    fun surroundingWhitespaceIsIgnored() {
        assertEquals(1, library.matchingQuery("  dune  ").size)
    }

    /**
     * The extension is stripped for display, so it is not part of the title a
     * reader can see - and so not something they can search for.
     */
    @Test
    fun extensionIsNotSearchable() {
        assertEquals(emptyList(), library.matchingQuery("pdf"))
    }

    @Test
    fun matchesKeepTheirOriginalOrder() {
        val titles = library.matchingQuery("").map { it.document.fileName }

        assertEquals(listOf("Moby Dick.pdf", "The Hobbit.pdf", "dune.pdf"), titles)
    }
}
