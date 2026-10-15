package io.lukia.core.infrastructure.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The two ways a PDF spells text, and the stretch where getting it wrong is
 * invisible until somebody reads a chapter list.
 *
 * PDFDocEncoding is Latin-1 apart from 0x18-0x1F and 0x80-0xA0, which is exactly
 * where a typeset book's punctuation lives. Read as Latin-1, an em dash in
 * "Chapter One - The Whale" comes back as a stray quotation mark, an ellipsis as
 * a dagger, a curly apostrophe as an angle bracket. Nothing else in the suite
 * reads a byte through this table.
 */
class PdfDocEncodingTest {

    private fun decoded(vararg bytes: Int): String =
        decodeText(bytes.map { it.toByte() }.toByteArray())

    @Test
    fun plainAsciiIsItself() {
        assertEquals("Chapter One", decoded(*"Chapter One".map { it.code }.toIntArray()))
    }

    /** The reason the table is here at all. */
    @Test
    fun theTypographersDashesAreNotLatinOnes() {
        assertEquals("—", decoded(0x84))
        assertEquals("–", decoded(0x85))
    }

    @Test
    fun theQuotationMarksABookIsSetInAreTheCurlyOnes() {
        assertEquals("“", decoded(0x8D))
        assertEquals("”", decoded(0x8E))
        assertEquals("‘", decoded(0x8F))
        assertEquals("’", decoded(0x90))
    }

    @Test
    fun theEllipsisIsOneCharacterRatherThanThree() {
        assertEquals("…", decoded(0x83))
    }

    @Test
    fun theLigaturesATypesetterLeavesBehindAreKept() {
        assertEquals("ﬁ", decoded(0x93))
        assertEquals("ﬂ", decoded(0x94))
    }

    /** The smaller stretch, which holds the accents a name is set with. */
    @Test
    fun theDiacriticStretchIsNotControlCharacters() {
        assertEquals("˘", decoded(0x18))
        assertEquals("˜", decoded(0x1F))
    }

    @Test
    fun everythingOutsideThoseTwoStretchesIsLatinOne() {
        assertEquals("é", decoded(0xE9))
        assertEquals("ü", decoded(0xFC))
        assertEquals("A", decoded(0x41))
    }

    /**
     * The other spelling: a byte-order mark, and then UTF-16 big-endian. This is
     * what a file written by anything modern uses for a title with a character
     * Latin-1 has no room for.
     */
    @Test
    fun aByteOrderMarkMeansTheRestIsUtfSixteen() {
        assertEquals(
            "Hei",
            decoded(0xFE, 0xFF, 0x00, 0x48, 0x00, 0x65, 0x00, 0x69),
        )
    }

    @Test
    fun utfSixteenCarriesWhatTheOtherTableCannot() {
        assertEquals("П", decoded(0xFE, 0xFF, 0x04, 0x1F))
        assertEquals("あ", decoded(0xFE, 0xFF, 0x30, 0x42))
    }

    /**
     * A mark with an odd number of bytes after it is a truncated string, which
     * is ordinary input. What is there is read; the half character is not.
     */
    @Test
    fun aTruncatedUtfSixteenStringGivesUpWhatItHas() {
        assertEquals("H", decoded(0xFE, 0xFF, 0x00, 0x48, 0x00))
    }

    @Test
    fun aMarkAndNothingElseIsNothing() {
        assertEquals("", decoded(0xFE, 0xFF))
    }

    @Test
    fun noBytesAreNoText() {
        assertEquals("", decoded())
    }

    /**
     * Only a big-endian mark is a mark. `\xFF\xFE` is not one a PDF writes, and
     * reading it as though it were turns the whole title into the wrong script.
     */
    @Test
    fun aLittleEndianMarkIsNotOne() {
        assertEquals(2, decoded(0xFF, 0xFE).length)
    }
}
