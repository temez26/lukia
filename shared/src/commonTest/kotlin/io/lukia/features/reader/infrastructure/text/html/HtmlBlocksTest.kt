package io.lukia.features.reader.infrastructure.text.html

import io.lukia.features.reader.domain.text.BlockAlignment
import io.lukia.features.reader.domain.text.Emphasis
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.plainText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HtmlBlocksTest {

    @Test
    fun readsHeadingsAndParagraphs() {
        val blocks = htmlBlocks(
            """
            <body>
              <h2>Chapter One</h2>
              <p>The first paragraph.</p>
              <p>The second.</p>
            </body>
            """.trimIndent(),
        )

        val heading = blocks.first() as TextBlock.Heading

        assertEquals("Chapter One", heading.text.plainText())
        assertEquals(2, heading.level)
        assertEquals(
            listOf("The first paragraph.", "The second."),
            blocks.drop(1).map { (it as TextBlock.Paragraph).text.plainText() },
        )
    }

    /**
     * A paragraph wrapped at eighty columns in the source file is one paragraph,
     * not one per line - HTML says so, and a book that ignored it would be full
     * of breaks the author never wrote.
     */
    @Test
    fun whitespaceInTheSourceFileIsCollapsed() {
        val blocks = htmlBlocks(
            """
            <p>
              It was the best of times,
              it was the worst of times.
            </p>
            """.trimIndent(),
        )

        assertEquals(
            "It was the best of times, it was the worst of times.",
            (blocks.single() as TextBlock.Paragraph).text.plainText(),
        )
    }

    @Test
    fun emphasisAppliesToTheWordsInsideIt() {
        val blocks = htmlBlocks("<p>the <em>Pequod</em> sailed <strong>at dawn</strong></p>")

        val runs = (blocks.single() as TextBlock.Paragraph).text

        assertEquals(
            listOf(emptySet(), setOf(Emphasis.Italic), emptySet(), setOf(Emphasis.Bold)),
            runs.map { it.emphasis },
        )
        assertEquals("the Pequod sailed at dawn", runs.plainText())
    }

    @Test
    fun nestedEmphasisCarriesBothMarks() {
        val blocks = htmlBlocks("<p><b>bold and <i>both</i></b></p>")

        val runs = (blocks.single() as TextBlock.Paragraph).text

        assertEquals(setOf(Emphasis.Bold), runs.first().emphasis)
        assertEquals(setOf(Emphasis.Bold, Emphasis.Italic), runs.last().emphasis)
    }

    @Test
    fun listsCarryTheirOwnMarkers() {
        val blocks = htmlBlocks(
            "<ul><li>first</li><li>second</li></ul><ol><li>one</li><li>two</li></ol>",
        )

        val items = blocks.filterIsInstance<TextBlock.ListItem>()

        assertEquals(listOf("•", "•", "1.", "2."), items.map { it.marker })
        assertEquals(listOf("first", "second", "one", "two"), items.map { it.text.plainText() })
    }

    @Test
    fun aQuotationsOwnParagraphsAreStillQuoted() {
        val blocks = htmlBlocks("<blockquote><p>quoted</p></blockquote><p>not quoted</p>")

        assertTrue(blocks[0] is TextBlock.Quote)
        assertTrue(blocks[1] is TextBlock.Paragraph)
    }

    /** Preformatted text is the one thing here whose whitespace is content. */
    @Test
    fun preformattedTextKeepsItsLineBreaks() {
        val blocks = htmlBlocks("<pre>line one\n  line two</pre>")

        assertEquals("line one\n  line two", (blocks.single() as TextBlock.Code).text)
    }

    @Test
    fun horizontalRulesBecomeSeparators() {
        val blocks = htmlBlocks("<p>before</p><hr/><p>after</p>")

        assertTrue(blocks[1] is TextBlock.Separator)
    }

    @Test
    fun stylesheetsAndScriptsAreNotProse() {
        val blocks = htmlBlocks(
            """
            <head><title>Book</title><style>p { color: red }</style></head>
            <body><script>alert('hi')</script><p>only this</p></body>
            """.trimIndent(),
        )

        assertEquals("only this", (blocks.single() as TextBlock.Paragraph).text.plainText())
    }

    @Test
    fun illustrationsAreLookedUpByTheirSource() {
        val bytes = byteArrayOf(1, 2, 3)

        val blocks = htmlBlocks(
            """<p>text</p><img src="images/plate.png" alt="A plate"/>""",
        ) { source -> if (source == "images/plate.png") bytes else null }

        val figure = blocks.filterIsInstance<TextBlock.Figure>().single()

        assertEquals(bytes.toList(), figure.bytes.toList())
        assertEquals("A plate", figure.description)
    }

    /** An SVG-wrapped cover is how a great many EPUBs open. */
    @Test
    fun anSvgWrappedImageIsStillAnIllustration() {
        val bytes = byteArrayOf(4, 5, 6)

        val blocks = htmlBlocks(
            """<svg viewBox="0 0 600 800"><image xlink:href="cover.jpg"/></svg>""",
        ) { bytes }

        assertEquals(bytes.toList(), blocks.filterIsInstance<TextBlock.Figure>().single().bytes.toList())
    }

    /** A `<p>` nobody closed must not swallow the rest of the chapter. */
    @Test
    fun anUnclosedParagraphDoesNotAbsorbWhatFollowsIt() {
        val blocks = htmlBlocks("<p>first<p>second<h3>Heading</h3>")

        assertEquals(
            listOf("first", "second"),
            blocks.filterIsInstance<TextBlock.Paragraph>().map { it.text.plainText() },
        )
        assertEquals("Heading", blocks.filterIsInstance<TextBlock.Heading>().single().text.plainText())
    }

    @Test
    fun aLineBreakInsideAParagraphIsKept() {
        val blocks = htmlBlocks("<p>first line<br/>second line</p>")

        assertEquals(
            "first line\nsecond line",
            (blocks.single() as TextBlock.Paragraph).text.plainText(),
        )
    }

    /**
     * An id names a place in the chapter, and the place is the block that opens
     * there - which is what a contents entry pointing into a file has to land on.
     */
    @Test
    fun anIdBelongsToTheBlockThatOpensWhereItIsWritten() {
        val content = htmlContent(
            """<p>before</p><section id="two"><h1>Two</h1><p>after</p></section>""",
        )

        assertEquals(mapOf("two" to 1), content.anchors)
    }

    @Test
    fun anIdWithNothingAfterItBelongsToTheLastBlockThereIs() {
        val content = htmlContent("""<p>only</p><div id="end"></div>""")

        assertEquals(mapOf("end" to 0), content.anchors)
    }

    /** The blocks alone: what a chapter reads as is what these tests are about. */
    private fun htmlBlocks(
        html: String,
        image: (src: String) -> ByteArray? = { null },
    ): List<TextBlock> = htmlContent(html, image).blocks

    /**
     * The `align` attribute is how books have set their dedications, captions
     * and epigraphs for twenty years, and is by far the more common of the two.
     */
    @Test
    fun aParagraphKeepsTheAlignmentTheBookAskedFor() {
        val blocks = htmlBlocks(
            """<p align="center">For my parents.</p><p align="right">- H. M.</p><p>Call me.</p>""",
        )

        assertEquals(
            listOf(BlockAlignment.Center, BlockAlignment.Right, null),
            blocks.map { it.alignment },
        )
    }

    /** The modern spelling of the same thing. */
    @Test
    fun anInlineTextAlignIsReadTheSameWay() {
        val blocks = htmlBlocks(
            """<p style="margin:0; text-align: center; color:red">Centred.</p>""",
        )

        assertEquals(BlockAlignment.Center, blocks.single().alignment)
    }

    /**
     * A paragraph in a centred division is centred without saying so itself,
     * which is how books set their dedication pages.
     */
    @Test
    fun alignmentIsInheritedFromWhatAParagraphSitsInside() {
        val blocks = htmlBlocks(
            """<div align="center"><p>First.</p><p>Second.</p></div><p>After.</p>""",
        )

        assertEquals(
            listOf(BlockAlignment.Center, BlockAlignment.Center, null),
            blocks.map { it.alignment },
        )
    }

    /** What a paragraph says about itself beats what it sits inside. */
    @Test
    fun aParagraphsOwnAlignmentWinsOverTheOneItInherits() {
        val blocks = htmlBlocks("""<div align="center"><p align="left">Its own.</p></div>""")

        assertEquals(BlockAlignment.Left, blocks.single().alignment)
    }

    @Test
    fun headingsAndQuotationsCarryItToo() {
        val blocks = htmlBlocks(
            """<h1 align="center">Part One</h1><blockquote align="right"><p>Quoted.</p></blockquote>""",
        )

        assertEquals(
            listOf(BlockAlignment.Center, BlockAlignment.Right),
            blocks.map { it.alignment },
        )
    }

    @Test
    fun anAlignmentNobodyRecognisesIsNoAlignmentAtAll() {
        assertNull(htmlBlocks("""<p align="middle">Words.</p>""").single().alignment)
    }
}
