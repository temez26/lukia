package io.lukia.features.library

import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.features.library.presentation.LibraryFilter
import io.lukia.features.library.presentation.availableFilters
import io.lukia.features.library.presentation.matching
import kotlin.test.Test
import kotlin.test.assertEquals

class LibraryFilterTest {

    private fun document(
        title: String,
        type: DocumentType = DocumentType.PDF,
        favorite: Boolean = false
    ): DocumentInfo {
        val extension = if (type == DocumentType.EPUB) "epub" else "pdf"

        return DocumentInfo(
            document = PickedDocument(
                fileName = "$title.$extension",
                extension = extension,
                location = "/library/$title.$extension"
            ),
            type = type,
            exists = true,
            sizeBytes = 1024L,
            isFavorite = favorite
        )
    }

    private fun titles(documents: List<DocumentInfo>) =
        documents.map { it.document.fileName.substringBeforeLast(".") }

    @Test
    fun aLibraryOfOneFormatWithNoFavouritesOffersNothingToChoose() {
        val library = listOf(document("Dune"), document("Solaris"))

        // Only All - the caller takes a single filter as "leave the control off".
        assertEquals(listOf(LibraryFilter.All), library.availableFilters())
    }

    @Test
    fun favouritesEarnASegmentOnlyWhenSomeExist() {
        val without = listOf(document("Dune"))
        val with = listOf(document("Dune", favorite = true))

        assertEquals(listOf(LibraryFilter.All), without.availableFilters())
        assertEquals(
            listOf(LibraryFilter.All, LibraryFilter.Favourites),
            with.availableFilters()
        )
    }

    /** A format segment holding every book is the All segment under another name. */
    @Test
    fun aSingleFormatEarnsNoSegment() {
        val library = listOf(
            document("Dune", type = DocumentType.EPUB),
            document("Solaris", type = DocumentType.EPUB)
        )

        assertEquals(listOf(LibraryFilter.All), library.availableFilters())
    }

    @Test
    fun bothFormatsEarnSegmentsWhenBothArePresent() {
        val library = listOf(
            document("Dune", type = DocumentType.PDF),
            document("Solaris", type = DocumentType.EPUB)
        )

        assertEquals(
            listOf(LibraryFilter.All, LibraryFilter.Pdf, LibraryFilter.Epub),
            library.availableFilters()
        )
    }

    @Test
    fun filterAndQueryBothApply() {
        val library = listOf(
            document("Dune", type = DocumentType.PDF, favorite = true),
            document("Dune Messiah", type = DocumentType.EPUB),
            document("Solaris", type = DocumentType.PDF, favorite = true)
        )

        assertEquals(listOf("Dune"), titles(library.matching(LibraryFilter.Favourites, "dune")))
        assertEquals(
            listOf("Dune", "Solaris"),
            titles(library.matching(LibraryFilter.Favourites, ""))
        )
        assertEquals(
            listOf("Dune Messiah"),
            titles(library.matching(LibraryFilter.Epub, ""))
        )
    }

    @Test
    fun allWithABlankQueryIsTheWholeLibrary() {
        val library = listOf(
            document("Dune", type = DocumentType.EPUB),
            document("Solaris")
        )

        assertEquals(library, library.matching(LibraryFilter.All, ""))
    }

    @Test
    fun matchesKeepTheirOriginalOrder() {
        val library = listOf(
            document("Zeta", favorite = true),
            document("Alpha", favorite = true)
        )

        assertEquals(
            listOf("Zeta", "Alpha"),
            titles(library.matching(LibraryFilter.Favourites, ""))
        )
    }
}
