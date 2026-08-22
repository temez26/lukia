package io.lukia.features.import.infrastructure.cover

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The first page of a PDF, drawn by PDFKit into the picture the library shows
 * beside a book's title.
 */
class PdfCoverRendererTest {

    @Test
    fun drawsTheFirstPageAtTheSizeALibraryRowShows() {
        val cover = renderPdfCover(singlePagePdf())

        assertNotNull(cover)
        assertTrue(cover.isPng(), "a rendered cover came back in some other format")

        // Fitted inside the size asked for rather than filling it: the page is
        // taller than the box is, so it is the height that runs out first.
        assertTrue(
            longestEdgeOf(cover) in SHORTEST_THUMBNAIL_EDGE..LONGEST_THUMBNAIL_EDGE,
            "a cover ${longestEdgeOf(cover)} across does not fit the thumbnail asked for",
        )
    }

    @Test
    fun somethingThatIsNotAPdfHasNoCover() {
        assertNull(renderPdfCover("not a document".encodeToByteArray()))
    }

    @Test
    fun aFileWithNothingInItHasNoCover() {
        assertNull(renderPdfCover(ByteArray(0)))
    }

    private fun ByteArray.isPng(): Boolean =
        size > 1 && this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte()

    private companion object {
        /** The 240 by 320 the renderer asks PDFKit for. */
        const val SHORTEST_THUMBNAIL_EDGE = 240
        const val LONGEST_THUMBNAIL_EDGE = 320
    }
}
