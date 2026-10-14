package io.lukia.features.library.infrastructure.importing

import io.lukia.core.infrastructure.importing.cover.longestEdgeOf
import io.lukia.core.infrastructure.importing.cover.singlePagePdf
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * How the import reads a document on this platform: through a handle on the
 * file, and never by loading it.
 */
class IOSDocumentSourceTest {

    @Test
    fun readsAWindowOutOfTheMiddleOfAFile() {
        val document = ByteArray(1000) { it.toByte() }

        openedDocument(document) { source ->
            assertEquals(1000L, source.size)
            assertContentEquals(document.copyOfRange(400, 410), source.read(400, 10))
        }
    }

    @Test
    fun readingPastTheEndAnswersWithWhatIsThere() {
        val document = ByteArray(16) { it.toByte() }

        openedDocument(document) { source ->
            assertContentEquals(document.copyOfRange(10, 16), source.read(10, 100))
            assertContentEquals(ByteArray(0), source.read(16, 8))
            assertContentEquals(ByteArray(0), source.read(-1, 8))
        }
    }

    @Test
    fun drawsTheFirstPageAtTheSizeALibraryRowShows() {
        val cover = openedDocument(singlePagePdf()) { it.renderFirstPage() }

        assertNotNull(cover)
        assertTrue(cover.isPng(), "a rendered cover came back in some other format")

        // Fitted inside the size asked for rather than filling it: the page is
        // taller than the box is, so it is the height that runs out first.
        assertTrue(
            longestEdgeOf(cover) in SHORTEST_THUMBNAIL_EDGE..LONGEST_THUMBNAIL_EDGE,
            "a cover ${longestEdgeOf(cover)} across does not fit the thumbnail asked for",
        )
    }

    /**
     * The cover is drawn from a second handle on the same file, and the first
     * one has to survive it - the metadata is read through this source after.
     */
    @Test
    fun theSourceIsStillReadableAfterACoverHasBeenDrawnFromIt() {
        val pdf = singlePagePdf()

        openedDocument(pdf) { source ->
            assertNotNull(source.renderFirstPage())
            assertContentEquals(pdf.copyOfRange(0, 8), source.read(0, 8))
        }
    }

    @Test
    fun somethingThatIsNotAPdfHasNoCover() {
        assertNull(openedDocument("not a document".encodeToByteArray()) { it.renderFirstPage() })
    }

    @Test
    fun aFileWithNothingInItHasNoCover() {
        assertNull(openedDocument(ByteArray(0)) { it.renderFirstPage() })
    }

    private fun ByteArray.isPng(): Boolean =
        size > 1 && this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte()

    private companion object {
        /** The 240 by 320 the renderer asks PDFKit for. */
        const val SHORTEST_THUMBNAIL_EDGE = 240
        const val LONGEST_THUMBNAIL_EDGE = 320
    }
}
