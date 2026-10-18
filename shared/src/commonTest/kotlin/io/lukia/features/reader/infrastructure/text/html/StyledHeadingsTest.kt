package io.lukia.features.reader.infrastructure.text.html

import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.plainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Headings a book makes with its stylesheet rather than its tags: a division
 * set bold and larger than the text, as converted books write their sections.
 */
class StyledHeadingsTest {

    private val css = """
        /* section titles */
        .class_sM1 {font-size: 1.2em; font-weight: bold; margin-top: 0.248em}
        p.part, .big {font-size: 160%; font-weight: 700}
        .emph {font-weight: bold}
        .large-only {font-size: 1.4em}
        div > .nested {font-size: 2em; font-weight: bold}
    """.trimIndent()

    @Test
    fun boldAndLargerClassesAreHeadingsAndNothingElseIs() {
        assertEquals(mapOf("class_sM1" to 3, "part" to 2, "big" to 2), headingClasses(css))
    }

    @Test
    fun aDivisionInAHeadingClassIsReadAsAHeading() {
        val blocks = htmlContent(
            """<body><div class="class_sM1">Multiple Elif Blocks</div><div class="x">Words.</div></body>""",
            styledHeadings = headingClasses(css),
        ).blocks

        val heading = blocks[0] as TextBlock.Heading
        assertEquals("Multiple Elif Blocks" to 3, heading.text.plainText() to heading.level)
        assertTrue(blocks[1] is TextBlock.Paragraph)
    }

    /** Set like a heading is not enough when it runs to a paragraph's length. */
    @Test
    fun aLongParagraphInAHeadingClassStaysAParagraph() {
        val long = "A sentence that goes on. ".repeat(10)

        val blocks = htmlContent(
            """<body><div class="big">$long</div></body>""",
            styledHeadings = headingClasses(css),
        ).blocks

        assertTrue(blocks.single() is TextBlock.Paragraph)
    }
}
