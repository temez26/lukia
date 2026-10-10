package io.lukia.core.infrastructure.pdf

import io.lukia.core.infrastructure.ByteArraySource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Finding the objects in a file, which is the half of PDF reading that the
 * outline and the metadata both stand on.
 *
 * Tested here rather than only through `PdfOutlineReader` and
 * `PdfMetadataReader`, which only ever see the paths a well-formed 1.4 file
 * takes. The three cases below - objects packed into a stream, encryption taken
 * off, encryption still on - are each a branch no other test reaches, and each
 * costs a reader their chapter list when it is wrong.
 */
class PdfObjectIndexTest {

    private fun index(bytes: ByteArray) = PdfObjectIndex(ByteArraySource(bytes))

    @Test
    fun findsTheCatalogAFileWritesDirectly() {
        val catalog = assertNotNull(index(MinimalPdf.withOutline()).catalog())

        assertEquals("Catalog", catalog["Type"].asName)
    }

    /**
     * The case a 1.5 or later file is: the catalog is not in the file at all,
     * it is inside an object stream, and getting to it means unpacking that
     * stream and reading its header of `objectNumber offset` pairs.
     */
    @Test
    fun findsACatalogPackedInsideAnObjectStream() {
        val catalog = assertNotNull(
            index(MinimalPdf.withObjectStreams()).catalog(),
            "the catalog was not found inside the object stream",
        )

        assertEquals("Catalog", catalog["Type"].asName)
        assertEquals(PdfValue.Reference(6, 0), catalog["Outlines"])
    }

    /**
     * And every object in that stream, not just the first - the offsets in the
     * header are relative to `/First`, so one added back wrongly leaves the
     * objects after it reading from the middle of their neighbour.
     */
    @Test
    fun everyObjectInTheStreamComesBackWhole() {
        val index = index(MinimalPdf.withObjectStreams())

        val outlines = assertNotNull(index.resolve(PdfValue.Reference(6, 0)).asDictionary)
        assertEquals("Outlines", outlines["Type"].asName)
        assertEquals(2, outlines["Count"].asInt)

        val first = assertNotNull(index.resolve(outlines["First"]).asDictionary)
        val last = assertNotNull(index.resolve(outlines["Last"]).asDictionary)

        assertEquals(MinimalPdf.FIRST_CHAPTER_TITLE, first["Title"].asText)
        assertEquals(MinimalPdf.SECOND_CHAPTER_TITLE, last["Title"].asText)
    }

    /** An object that is in the file directly is still read from the file. */
    @Test
    fun anObjectOutsideTheStreamIsStillFound() {
        val pages = assertNotNull(index(MinimalPdf.withObjectStreams()).resolve(PdfValue.Reference(2, 0)).asDictionary)

        assertEquals(3, pages["Count"].asInt)
    }

    /**
     * The file every shop selling DRM-free copies of an encrypted book produces:
     * the trailer that declared the encryption is still there as an earlier
     * revision, and a newer one that says nothing about it has been appended.
     *
     * Asking whether *any* trailer names an /Encrypt takes the whole book away.
     */
    @Test
    fun aBookWhoseEncryptionWasRemovedIsNotEncrypted() {
        assertFalse(
            index(MinimalPdf.withEncryptionRemoved()).isEncrypted,
            "a book that had its encryption removed was refused",
        )
    }

    /** And it still gives up what it is for: the chapters hanging off its catalog. */
    @Test
    fun aBookWhoseEncryptionWasRemovedStillGivesUpItsChapters() {
        val index = index(MinimalPdf.withEncryptionRemoved())
        val catalog = assertNotNull(index.catalog())
        val outlines = assertNotNull(index.resolve(catalog["Outlines"]).asDictionary)

        assertEquals(
            MinimalPdf.FIRST_CHAPTER_TITLE,
            index.resolve(outlines["First"]).asDictionary?.get("Title").asText,
        )
    }

    /**
     * The other direction, which is the one that has to keep working: a file
     * whose newest trailer does declare an /Encrypt is encrypted, and its
     * strings would come back as ciphertext.
     */
    @Test
    fun aBookThatIsStillEncryptedSaysSo() {
        assertTrue(index(MinimalPdf.stillEncrypted()).isEncrypted)
    }

    /** Which is why nothing is read out of its information dictionary. */
    @Test
    fun anEncryptedBookGivesUpNoInformationDictionary() {
        assertNull(
            index(MinimalPdf.stillEncrypted()).info(),
            "an encrypted book handed over strings that would be ciphertext",
        )
    }

    @Test
    fun theInformationDictionaryOfAPlainBookIsRead() {
        val info = assertNotNull(
            index(MinimalPdf.withInfo("/Title (The Whale) /Author (Herman Melville)")).info(),
        )

        assertEquals("The Whale", info["Title"].asText)
    }

    /**
     * A reference that points at nothing is a file saying something is missing,
     * not a file worth refusing.
     */
    @Test
    fun aReferenceToAnObjectThatIsNotThereResolvesToNothing() {
        assertNull(index(MinimalPdf.withOutline()).resolve(PdfValue.Reference(9999, 0)))
    }

    /** Bytes that are not a PDF index to nothing rather than raising. */
    @Test
    fun somethingThatIsNotAPdfHasNoCatalog() {
        assertNull(index("not a document, just some words".encodeToByteArray()).catalog())
    }
}
