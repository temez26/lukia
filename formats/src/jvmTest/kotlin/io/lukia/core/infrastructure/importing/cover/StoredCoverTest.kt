package io.lukia.core.infrastructure.importing.cover

import io.lukia.core.domain.document.DocumentType
import io.lukia.core.infrastructure.epub.MinimalEpub
import io.lukia.core.infrastructure.importing.openedDocument
import io.lukia.core.infrastructure.shrinkCoverImage
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the library is willing to keep as a cover.
 *
 * The size of a stored cover is not a matter of taste: Android reads a row
 * through a two-megabyte window and throws on one that does not fit, taking the
 * whole library screen with it. These are the tests that a cover cannot get that
 * large.
 */
class StoredCoverTest {

    @Test
    fun artworkLargerThanTheLimitIsStoredSmaller() {
        val artwork = noisyImage(width = 1600, height = 2400)

        val shrunk = shrinkCoverImage(artwork, MaxStoredCoverEdge)

        assertNotNull(shrunk)
        assertEquals(800, longestEdgeOf(shrunk))
        assertTrue(
            shrunk.size <= MaxStoredCoverBytes,
            "A shrunk cover was ${shrunk.size} bytes, over the $MaxStoredCoverBytes stored.",
        )
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
        val cover = openedDocument(singlePagePdf()) { extractCover(DocumentType.PDF, it) }

        assertNotNull(cover)
        assertTrue(longestEdgeOf(cover) <= MaxStoredCoverEdge)
        assertTrue(cover.size <= MaxStoredCoverBytes)
    }

    @Test
    fun anEpubsOwnArtworkIsStoredAtTheSizeTheLibraryKeeps() {
        val cover = openedDocument(epubWithCover(noisyImage(2000, 3000))) {
            extractCover(DocumentType.EPUB, it)
        }

        assertNotNull(cover)
        assertTrue(longestEdgeOf(cover) <= MaxStoredCoverEdge)
        assertTrue(cover.size <= MaxStoredCoverBytes)
    }

    /**
     * The case the byte limit exists for: something declared as a cover that no
     * decoder recognises, so there is no shrinking it. A monogram is a better
     * answer than a row the library cannot read back.
     */
    @Test
    fun artworkTooLargeToStoreAndImpossibleToShrinkIsDropped() {
        val notAnImage = Random(0).nextBytes(MaxStoredCoverBytes + 1)

        assertNull(openedDocument(epubWithCover(notAnImage)) { extractCover(DocumentType.EPUB, it) })
    }

    /** Noise rather than a flat colour: a blank image compresses to nothing. */
    private fun noisyImage(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val random = Random(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                image.setRGB(x, y, random.nextInt())
            }
        }

        return ByteArrayOutputStream().use { output ->
            ImageIO.write(image, "png", output)
            output.toByteArray()
        }
    }

    private fun longestEdgeOf(imageBytes: ByteArray): Int {
        val image = ImageIO.read(ByteArrayInputStream(imageBytes))

        return maxOf(image.width, image.height)
    }

    private fun singlePagePdf(): ByteArray {
        val output = ByteArrayOutputStream()

        PDDocument().use { document ->
            document.addPage(PDPage())
            document.save(output)
        }

        return output.toByteArray()
    }

    private fun epubWithCover(coverBytes: ByteArray): ByteArray =
        MinimalEpub.bookWithCover(coverBytes)
}
