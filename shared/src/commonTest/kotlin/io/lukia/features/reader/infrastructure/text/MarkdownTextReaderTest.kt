package io.lukia.features.reader.infrastructure.text

import io.lukia.features.reader.domain.Emphasis
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextSection
import io.lukia.features.reader.domain.plainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownTextReaderTest {

    @Test
    fun headingsDivideTheDocumentAndBecomeItsContents() {
        val content = read(
            """
            # The Book

            Opening words.

            # Chapter Two

            More words.
            """.trimIndent()
        )

        assertEquals(listOf("The Book", "Chapter Two"), content.sections.map { it.title })
        assertEquals(
            listOf("The Book" to 0, "Chapter Two" to 1),
            content.outline.orEmpty().map { it.title to it.pageIndex }
        )
    }

    @Test
    fun consecutiveLinesAreOneParagraph() {
        val content = read(
            """
            One line
            and its continuation.

            A second paragraph.
            """.trimIndent()
        )

        assertEquals(
            listOf("One line and its continuation.", "A second paragraph."),
            content.blocks().filterIsInstance<TextBlock.Paragraph>().map { it.text.plainText() }
        )
    }

    @Test
    fun readsEmphasisAndInlineCode() {
        val content = read("Some **bold**, some *italic* and some `code`.")

        val runs = (content.blocks().single() as TextBlock.Paragraph).text

        assertEquals("Some bold, some italic and some code.", runs.plainText())
        assertEquals(
            listOf(setOf(Emphasis.Bold), setOf(Emphasis.Italic), setOf(Emphasis.Monospace)),
            runs.map { it.emphasis }.filter { it.isNotEmpty() }
        )
    }

    /** An identifier is not emphasis, however many underscores it has. */
    @Test
    fun underscoresInsideAWordAreLeftAlone() {
        val content = read("The value of max_page_scale is fixed.")

        val runs = (content.blocks().single() as TextBlock.Paragraph).text

        assertEquals("The value of max_page_scale is fixed.", runs.plainText())
        assertTrue(runs.all { it.emphasis.isEmpty() })
    }

    @Test
    fun linksKeepTheirTextAndImagesTheirAltText() {
        val content = read("See [the guide](https://example.com) and ![a diagram](plate.png).")

        assertEquals(
            "See the guide and a diagram.",
            (content.blocks().single() as TextBlock.Paragraph).text.plainText()
        )
    }

    @Test
    fun listsKeepTheNumbersTheDocumentWrote() {
        val content = read(
            """
            - first
            - second

            4. fourth
            5. fifth
            """.trimIndent()
        )

        val items = content.blocks().filterIsInstance<TextBlock.ListItem>()

        assertEquals(listOf("•", "•", "4.", "5."), items.map { it.marker })
        assertEquals(listOf("first", "second", "fourth", "fifth"), items.map { it.text.plainText() })
    }

    @Test
    fun fencedCodeKeepsItsLayoutAndIsNotReadAsMarkdown() {
        val content = read(
            """
            Before.

            ```kotlin
            fun main() {
                println("*not italic*")
            }
            ```

            After.
            """.trimIndent()
        )

        assertEquals(
            "fun main() {\n    println(\"*not italic*\")\n}",
            content.blocks().filterIsInstance<TextBlock.Code>().single().text
        )
    }

    @Test
    fun quotedLinesBecomeQuotations() {
        val content = read("> Call me Ishmael.")

        assertEquals(
            "Call me Ishmael.",
            (content.blocks().single() as TextBlock.Quote).text.plainText()
        )
    }

    /**
     * A run of dashes underlines the line above it where there is one, and
     * divides the page where there is not - which is the one genuine ambiguity
     * in the syntax.
     */
    @Test
    fun dashesUnderlineAHeadingButOtherwiseDividieThePage() {
        val content = read(
            """
            Chapter Title
            -------------

            Words.

            ---

            More words.
            """.trimIndent()
        )

        val blocks = content.blocks()

        assertEquals("Chapter Title", (blocks.first() as TextBlock.Heading).text.plainText())
        assertEquals(2, (blocks.first() as TextBlock.Heading).level)
        assertTrue(blocks.any { it is TextBlock.Separator })
    }

    @Test
    fun anEscapedAsteriskIsAnAsterisk() {
        val content = read("""A literal \*star\* here.""")

        assertEquals(
            "A literal *star* here.",
            (content.blocks().single() as TextBlock.Paragraph).text.plainText()
        )
    }

    private fun read(source: String) = readMarkdown(source.encodeToByteArray())

    private fun io.lukia.features.reader.domain.ReflowableContent.blocks(): List<TextBlock> =
        sections.flatMap(TextSection::blocks)
}
