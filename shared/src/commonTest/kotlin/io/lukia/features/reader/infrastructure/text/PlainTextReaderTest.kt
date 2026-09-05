package io.lukia.features.reader.infrastructure.text

import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextSection
import io.lukia.features.reader.domain.text.plainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlainTextReaderTest {

    /**
     * The decision this format turns on: a `.txt` book is hard-wrapped at
     * whatever column its writer used, and honouring those breaks gives a book
     * of one-line paragraphs the moment it is read at any other width.
     */
    @Test
    fun hardWrappedLinesAreOneParagraph() {
        val content = read(
            """
            It is a truth universally acknowledged, that a single man
            in possession of a good fortune, must be in want of a wife.

            However little known the feelings of such a man may be.
            """.trimIndent(),
        )

        assertEquals(
            listOf(
                "It is a truth universally acknowledged, that a single man " +
                    "in possession of a good fortune, must be in want of a wife.",
                "However little known the feelings of such a man may be.",
            ),
            content.paragraphs(),
        )
    }

    /** Verse and lists are the two places a break in a text file is deliberate. */
    @Test
    fun indentedAndBulletedLinesKeepTheirOwnBreaks() {
        val content = read(
            """
            A list follows:
            - first
            - second
            """.trimIndent(),
        )

        assertEquals("A list follows:\n- first\n- second", content.paragraphs().single())
    }

    @Test
    fun blankLinesAndWindowsLineEndingsBothSeparateParagraphs() {
        val content = read("first\r\n\r\nsecond\r\n\r\n\r\nthird")

        assertEquals(listOf("first", "second", "third"), content.paragraphs())
    }

    /** Nothing in a text file says what it is, so a chapter list would be invented. */
    @Test
    fun plainTextOffersNoChaptersRatherThanNoAnswer() {
        val content = read("Just some words.")

        assertEquals(emptyList(), content.outline)
    }

    /**
     * One item holding a whole book is laid out in full every time it is drawn,
     * and gives the scrollbar a single position to report.
     */
    @Test
    fun aLongUnbrokenFileIsStillDividedIntoSections() {
        val content = read((1..1200).joinToString(separator = "\n\n") { "Paragraph $it." })

        assertTrue(content.sections.size > 1, "expected more than one section")
        assertEquals(1200, content.sections.sumOf { it.blocks.size })
    }

    /**
     * A word the writer's editor broke across the wrap is one word.
     *
     * The same rule the PDF reader follows, which had it written down and this
     * one did not - so the format most likely to be hard-wrapped was the one
     * that handed the reader, and a translator, "exam- ple".
     */
    @Test
    fun aWordBrokenAcrossTheWrapIsJoinedBackUp() {
        val content = read("There is nothing extra-\nordinary about this sentence.")

        assertEquals(
            listOf("There is nothing extra-ordinary about this sentence."),
            content.paragraphs(),
        )
    }

    /**
     * Except a soft hyphen, which is only ever a note about where a word may be
     * broken. Once the break has gone there is nothing left for it to say, and
     * leaving it in puts an invisible character in the middle of a word - which
     * is a word a search will not find and a translator will not know.
     */
    @Test
    fun aSoftHyphenAtTheWrapGoesWithTheBreak() {
        val content = read("There is nothing extra\u00AD\nordinary about this sentence.")

        assertEquals(
            listOf("There is nothing extraordinary about this sentence."),
            content.paragraphs(),
        )
    }

    /** Written on a Windows machine is not a different book. */
    @Test
    fun carriageReturnsAreNotPartOfTheText() {
        val content = read("First paragraph.\r\n\r\nSecond paragraph.\r\n")

        assertEquals(listOf("First paragraph.", "Second paragraph."), content.paragraphs())
    }

    private fun read(source: String) = readPlainText(source.encodeToByteArray())

    private fun io.lukia.features.reader.domain.ReflowableContent.paragraphs(): List<String> =
        sections.flatMap(TextSection::blocks)
            .filterIsInstance<TextBlock.Paragraph>()
            .map { it.text.plainText() }
}
