package io.lukia.features.reader.infrastructure.text

import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextSection
import io.lukia.features.reader.domain.plainText
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

    private fun read(source: String) = readPlainText(source.encodeToByteArray())

    private fun io.lukia.features.reader.domain.ReflowableContent.paragraphs(): List<String> =
        sections.flatMap(TextSection::blocks)
            .filterIsInstance<TextBlock.Paragraph>()
            .map { it.text.plainText() }
}
