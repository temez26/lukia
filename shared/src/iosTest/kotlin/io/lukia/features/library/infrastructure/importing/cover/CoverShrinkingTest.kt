package io.lukia.features.library.infrastructure.importing.cover

import io.lukia.core.domain.document.DocumentType
import io.lukia.core.infrastructure.shrinkCoverImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the library is willing to keep as a cover, on iOS.
 *
 * The same questions the desktop's own `StoredCoverTest` asks, against UIKit
 * rather than ImageIO. Worth asking twice: the size of a stored cover is not a
 * matter of taste - Android reads a row through a two-megabyte window and
 * throws on one that does not fit - and a book imported on a phone is stored in
 * the same database as one imported anywhere else.
 */
class CoverShrinkingTest {

    @Test
    fun artworkLargerThanTheLimitIsStoredSmaller() {
        val artwork = noisyImage(width = 1600, height = 2400)

        val shrunk = shrinkCoverImage(artwork, MaxStoredCoverEdge)

        assertNotNull(shrunk)
        assertEquals(MaxStoredCoverEdge, longestEdgeOf(shrunk))
        assertTrue(
            shrunk.size <= MaxStoredCoverBytes,
            "A shrunk cover was ${shrunk.size} bytes, over the $MaxStoredCoverBytes stored.",
        )
    }

    /** JPEG, as the contract says: a cover has nothing behind it to show through. */
    @Test
    fun aShrunkCoverIsWrittenOutAsJpeg() {
        val shrunk = shrinkCoverImage(noisyImage(width = 1600, height = 2400), MaxStoredCoverEdge)

        assertNotNull(shrunk)
        assertEquals(0xFF.toByte(), shrunk[0])
        assertEquals(0xD8.toByte(), shrunk[1])
    }

    /** Re-encoding artwork that already fits would cost quality for nothing. */
    @Test
    fun artworkAlreadySmallEnoughIsKeptAsItIs() {
        assertNull(shrinkCoverImage(noisyImage(width = 400, height = 600), MaxStoredCoverEdge))
    }

    @Test
    fun somethingThatIsNotAnImageCannotBeShrunk() {
        assertNull(shrinkCoverImage("not a cover".encodeToByteArray(), MaxStoredCoverEdge))
    }

    @Test
    fun aRenderedPdfPageIsStoredAtTheSizeTheLibraryKeeps() {
        val cover = extractCover(DocumentType.PDF, singlePagePdf())

        assertNotNull(cover)
        assertTrue(longestEdgeOf(cover) <= MaxStoredCoverEdge)
        assertTrue(cover.size <= MaxStoredCoverBytes)
    }
}
