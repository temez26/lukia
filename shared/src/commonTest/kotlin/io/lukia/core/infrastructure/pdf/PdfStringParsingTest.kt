package io.lukia.core.infrastructure.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The two shapes a PDF string takes, and the escapes inside them.
 *
 * This is where a chapter title arrives from, so every case below is a title a
 * real book has: a heading with a parenthetical, a dash written as an octal
 * escape, a title wrapped across two lines of the source file. Getting any wrong
 * truncates the title there rather than failing, which is why it needs pinning.
 */
class PdfStringParsingTest {

    private fun text(source: String): String? =
        (PdfObjectParser(source.encodeToByteArray()).parseValue() as? PdfValue.Text)?.value

    @Test
    fun aPlainLiteralStringIsItsContents() {
        assertEquals("Introduction", text("(Introduction)"))
    }

    /** A heading with a parenthetical in it, which nothing escapes. */
    @Test
    fun balancedParenthesesInsideAStringAreNotItsEnd() {
        assertEquals("Chapter 3 (revised)", text("(Chapter 3 (revised))"))
    }

    @Test
    fun parenthesesNestedSeveralDeepAreStillBalanced() {
        assertEquals("a (b (c) d) e", text("(a (b (c) d) e)"))
    }

    /** An unbalanced one is escaped instead, and the escape is not part of it. */
    @Test
    fun anEscapedParenthesisIsACharacterRatherThanADelimiter() {
        assertEquals("Chapter 3 )", text("""(Chapter 3 \))"""))
        assertEquals("Chapter 3 (", text("""(Chapter 3 \()"""))
    }

    @Test
    fun theNamedEscapesAreTheCharactersTheyName() {
        assertEquals("a\nb", text("""(a\nb)"""))
        assertEquals("a\rb", text("""(a\rb)"""))
        assertEquals("a\tb", text("""(a\tb)"""))
    }

    /** A backslash before anything else is that thing, backslashes included. */
    @Test
    fun anEscapeBeforeSomethingOrdinaryIsThatThing() {
        assertEquals("a\\b", text("""(a\\b)"""))
        assertEquals("aqb", text("""(a\qb)"""))
    }

    /** `\101` is `A`: three octal digits, which is how a producer writes a byte it will not emit. */
    @Test
    fun aThreeDigitOctalEscapeIsTheByteItSpells() {
        assertEquals("A", text("""(\101)"""))
    }

    /**
     * Fewer than three is legal, and ends at the first byte that is not an octal
     * digit - which is how `\50` is written next to a `9` without the `9` being
     * swallowed into it.
     */
    @Test
    fun anOctalEscapeEndsAtTheFirstByteThatIsNotOne() {
        assertEquals("(9", text("""(\0509)"""))
        assertEquals("+9", text("""(\539)"""))
        assertEquals("A9", text("""(\1019)"""))
    }

    /**
     * The whole point of the octal escape, and the reason [decodeText] sits
     * behind it: a producer that will not emit 0x84 writes `\204`, and the
     * reader has to arrive at an em dash either way.
     */
    @Test
    fun anOctalEscapeGoesThroughTheEncodingTableLikeAPlainByte() {
        assertEquals("Chapter One — The Whale", text("""(Chapter One \204 The Whale)"""))
    }

    /**
     * A backslash at the end of a line is a title wrapped in the source file,
     * not a line break in the title.
     */
    @Test
    fun aBackslashAtTheEndOfALineJoinsTheTwoHalves() {
        assertEquals("Moby Dick; or, The Whale", text("(Moby Dick; or, \\\n" + "The Whale)"))
        assertEquals("Moby Dick", text("(Moby \\\r\n" + "Dick)"))
    }

    @Test
    fun aStringThatIsNeverClosedIsNotAString() {
        assertNull(text("(Introduction"))
    }

    @Test
    fun anEmptyStringIsAStringWithNothingInIt() {
        assertEquals("", text("()"))
    }

    @Test
    fun aHexStringIsTheBytesItsDigitsSpell() {
        assertEquals("Hello", text("<48656C6C6F>"))
    }

    @Test
    fun whitespaceInsideAHexStringIsNotADigit() {
        assertEquals("He", text("<48 65>"))
        assertEquals("He", text("<48\n65>"))
    }

    /** An odd number of digits is padded with a trailing zero, not dropped. */
    @Test
    fun anOddNumberOfHexDigitsIsPaddedRatherThanRefused() {
        assertEquals("H`", text("<48 6>"))
    }

    /** A hex string is the other place a byte-order mark turns up. */
    @Test
    fun aHexStringCarriesUtfSixteenTheSameWay() {
        assertEquals("Hei", text("<FEFF004800650069>"))
    }

    @Test
    fun aDigitThatIsNotOneMakesTheHexStringALoss() {
        assertNull(text("<48ZZ>"))
    }

    @Test
    fun aHexStringThatIsNeverClosedIsNotAString() {
        assertNull(text("<4865"))
    }
}
