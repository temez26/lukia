package io.lukia.features.reader.infrastructure.text.epub

import io.lukia.features.reader.infrastructure.text.MAX_BLOCKS_PER_SECTION
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Books that hold every chapter in one spine item, which is what Leanpub, Pandoc
 * and several other tools produce.
 *
 * Read the way a divided book is read, such a file is one section holding the
 * whole book and a contents panel whose every row leads to the top of it.
 */
class EpubSingleFileBookTest {

    @Test
    fun aFileHoldingEveryChapterIsDividedIntoThem() = runTest {
        val content = epubRead(singleFileBook())

        assertNotNull(content)
        assertEquals(
            listOf("One", "Two", "Three"),
            content.sections.map { it.title },
        )
    }

    /** A row per chapter that all lead to the same place is a panel that does nothing. */
    @Test
    fun theContentsLeadsToTheChapterItsAnchorNames() = runTest {
        val content = epubRead(singleFileBook())

        assertNotNull(content)
        assertEquals(
            listOf("One" to 0, "Two" to 1, "Three" to 2),
            content.outline.orEmpty().map { it.title to it.pageIndex },
        )
    }

    /** No chapter of a divided book is large enough to stutter when it is drawn. */
    @Test
    fun noSectionIsLargerThanTheLayoutBudget() = runTest {
        val content = epubRead(singleFileBook(paragraphs = 900))

        assertNotNull(content)
        assertTrue(content.sections.all { it.blocks.size <= MAX_BLOCKS_PER_SECTION })
    }

    /**
     * The spine keeps its authority wherever the book has used it: a file the
     * reader can lay out is one section, however many headings are inside it.
     */
    @Test
    fun aFileTheReaderCanLayOutIsLeftAsTheOneSectionTheSpineSaysItIs() = runTest {
        val content = epubRead(singleFileBook(paragraphs = 4))

        assertNotNull(content)
        assertEquals(1, content.sections.size)
        assertEquals(
            listOf(0, 0, 0),
            content.outline.orEmpty().map { it.pageIndex },
        )
    }

    private fun singleFileBook(paragraphs: Int = 200): ByteArray {
        val titles = listOf("One", "Two", "Three")

        val body = titles.mapIndexed { index, title ->
            val prose = (1..paragraphs).joinToString("") { "<p>Paragraph $it of $title.</p>" }

            """<section id="chapter-$index"><h1>$title</h1>$prose</section>"""
        }

        val navigation = titles.mapIndexed { index, title ->
            """<li><a href="text/book.xhtml#chapter-$index">$title</a></li>"""
        }

        return epub(
            manifest = """
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml"
                      properties="nav"/>
                <item id="book" href="text/book.xhtml" media-type="application/xhtml+xml"/>
            """.trimIndent(),
            spine = """<itemref idref="book"/>""",
            entries = mapOf(
                "OEBPS/text/book.xhtml" to "<html><body>${body.joinToString("")}</body></html>",
                "OEBPS/nav.xhtml" to """
                    <html xmlns:epub="http://www.idpf.org/2007/ops">
                      <body>
                        <nav epub:type="toc"><ol>${navigation.joinToString("")}</ol></nav>
                      </body>
                    </html>
                """.trimIndent(),
            ),
        )
    }
}
