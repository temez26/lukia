package io.lukia.core.infrastructure.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one pass over a file that finds where its objects sit.
 *
 * Everything above this reads objects by number, so an object this misses does
 * not come back damaged - it comes back missing, and a book loses whichever
 * piece it was. The window is the reason that is worth pinning: a long book is
 * read a megabyte at a time, and an object header that lands across a boundary
 * is only found because the windows overlap. Nothing else in the suite has a
 * fixture long enough to reach that.
 */
class PdfFileScanTest {

    private fun scan(bytes: ByteArray, windowBytes: Int = 1 shl 20) =
        scanPdfObjects(ByteArrayPdfSource(bytes), windowBytes)

    @Test
    fun findsEveryObjectAFileDeclares() {
        val scan = scan(MinimalPdf.withOutline())

        // The catalog, the page tree, three pages, the outline and its two
        // entries, and three content streams.
        assertEquals((1..11).toSet(), scan.offsets.keys)
    }

    @Test
    fun findsTheTrailer() {
        assertEquals(1, scan(MinimalPdf.withOutline()).trailerOffsets.size)
    }

    /** A file whose encryption was removed carries the trailer of each revision. */
    @Test
    fun findsATrailerForEveryRevision() {
        assertEquals(2, scan(MinimalPdf.withEncryptionRemoved()).trailerOffsets.size)
    }

    @Test
    fun namesTheObjectThatCarriesAnObjectStream() {
        assertEquals(listOf(12), scan(MinimalPdf.withObjectStreams()).objectStreamNumbers)
    }

    /**
     * The case the overlap exists for, and the only one that cannot be reached
     * with a fixture of a sane size at the real window length.
     *
     * The header is `<number> <generation> obj`, and it is found by looking for
     * `obj` and walking backwards over the two numbers - so a window that begins
     * between the number and the keyword sees a keyword with nothing in front of
     * it, and the next window along has to be the one that finds it whole.
     */
    @Test
    fun findsAnObjectHeaderThatLandsAcrossAWindowBoundary() {
        val document = MinimalPdf.withOutline()

        // Every window length from just past the overlap to well past the file:
        // whichever one puts a header on a boundary, all of them have to agree
        // about what the file holds.
        val whole = scan(document).offsets

        (65..512).forEach { windowBytes ->
            assertEquals(
                whole,
                scan(document, windowBytes).offsets,
                "a window of $windowBytes bytes read the file differently",
            )
        }
    }

    /** And the objects it found are still readable at the offsets it recorded. */
    @Test
    fun anObjectFoundAcrossABoundaryIsStillReadableWhereItWasFound() {
        val document = MinimalPdf.withOutline()
        val index = PdfObjectIndex(ByteArrayPdfSource(document))

        assertEquals("Catalog", assertNotNull(index.catalog())["Type"].asName)

        (65..512).forEach { windowBytes ->
            val scan = scan(document, windowBytes)
            val catalogOffset = assertNotNull(scan.offsets[1], "no catalog at a window of $windowBytes")

            assertEquals(
                "Catalog",
                PdfObjectParser(document, 0)
                    .parseIndirectObject(catalogOffset.toInt())
                    .asDictionary
                    ?.get("Type").asName,
                "the catalog was recorded in the wrong place at a window of $windowBytes bytes",
            )
        }
    }

    /**
     * `endobj` ends in the same three letters `obj` does, and there is one of
     * them after every object in the file. Reading them as headers would double
     * the offsets and put each object one keyword past where it begins.
     */
    @Test
    fun theEndOfAnObjectIsNotTheStartOfOne() {
        val scan = scan((FileHeader + "1 0 obj\n<< /Type /Catalog >>\nendobj\n").encodeToByteArray())

        assertEquals(setOf(1), scan.offsets.keys)
    }

    /** Nor is the word inside an ordinary sentence. */
    @Test
    fun theLettersInsideAWordAreNotAHeader() {
        assertTrue(scan("an object of some description".encodeToByteArray()).offsets.isEmpty())
    }

    /**
     * An updated file appends a replacement rather than rewriting the object in
     * place, and the replacement is the one that counts.
     */
    @Test
    fun aReplacedObjectIsFoundWhereItWasReplaced() {
        val first = "1 0 obj\n<< /Type /Catalog >>\nendobj\n"
        val second = "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"

        val scan = scan((FileHeader + first + second).encodeToByteArray())

        assertEquals((FileHeader.length + first.length).toLong(), scan.offsets[1])
    }

    @Test
    fun aFileWithNothingInItHoldsNothing() {
        val scan = scan(ByteArray(0))

        assertTrue(scan.offsets.isEmpty())
        assertTrue(scan.trailerOffsets.isEmpty())
        assertNull(scan.objectStreamNumbers.firstOrNull())
    }

    private companion object {
        /**
         * A PDF opens with its version, so no object header ever sits at offset
         * zero - which is what lets one reaching the front of a window be
         * refused as truncated. The hand-made files below carry it for that
         * reason rather than for decoration.
         */
        const val FileHeader = "%PDF-1.4\n"
    }
}
