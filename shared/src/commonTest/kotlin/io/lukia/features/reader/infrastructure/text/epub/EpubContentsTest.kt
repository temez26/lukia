package io.lukia.features.reader.infrastructure.text.epub

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Contents as real books write them, which is not always as the specification
 * does: lists that are not the contents, labels with nowhere to go, labels
 * escaped twice, and links that point at the wrong chapter.
 */
class EpubContentsTest {

    @Test
    fun theNcxPageListIsNotReadAsChapters() = runTest {
        val ncx = """
            <ncx>
              <navMap>
                <navPoint><navLabel><text>One</text></navLabel><content src="one.xhtml"/></navPoint>
                <navPoint><navLabel><text>Two</text></navLabel><content src="two.xhtml"/></navPoint>
              </navMap>
              <pageList>
                <pageTarget type="normal"><navLabel><text>1</text></navLabel><content src="one.xhtml"/></pageTarget>
                <pageTarget type="normal"><navLabel><text>2</text></navLabel><content src="two.xhtml"/></pageTarget>
              </pageList>
            </ncx>
        """.trimIndent()

        assertEquals(listOf("One", "Two"), contentsOf(ncx = ncx).map { it.first })
    }

    /** A page list that does not say what it is came first and was taken as the contents. */
    @Test
    fun theListMarkedAsContentsWinsOverAnUnmarkedOne() = runTest {
        val nav = """
            <html><body>
              <nav><ol><li><a href="one.xhtml">1</a></li><li><a href="two.xhtml">2</a></li></ol></nav>
              <nav role="doc-toc"><ol><li><a href="one.xhtml">One</a></li><li><a href="two.xhtml">Two</a></li></ol></nav>
            </body></html>
        """.trimIndent()

        assertEquals(listOf("One", "Two"), contentsOf(nav = nav).map { it.first })
    }

    /** A part title with no link of its own still groups the chapters under it. */
    @Test
    fun aPartTitleWithoutALinkLeadsToItsFirstChapter() = runTest {
        val nav = """
            <html><body>
              <nav epub:type="toc"><ol>
                <li><span>Part I</span>
                  <ol>
                    <li><a href="one.xhtml">One</a></li>
                    <li><a href="two.xhtml">Two</a></li>
                  </ol>
                </li>
              </ol></nav>
            </body></html>
        """.trimIndent()

        assertEquals(
            listOf(Triple("Part I", 0, 0), Triple("One", 0, 1), Triple("Two", 1, 1)),
            contentsOf(nav = nav).map { (title, section, level) -> Triple(title, section, level) },
        )
    }

    @Test
    fun aLabelEscapedTwiceReadsAsItsText() = runTest {
        val ncx = """
            <ncx><navMap>
              <navPoint><navLabel><text>&amp;#160;Foreword</text></navLabel><content src="one.xhtml"/></navPoint>
              <navPoint><navLabel><text>It&amp;#8217;s Yours</text></navLabel><content src="two.xhtml"/></navPoint>
            </navMap></ncx>
        """.trimIndent()

        assertEquals(listOf("Foreword", "It’s Yours"), contentsOf(ncx = ncx).map { it.first })
    }

    /**
     * A converted book's contents sent a later chapter to an anchor inside the
     * first one. It goes to the file after the chapter before it instead.
     */
    @Test
    fun aLinkBackIntoAnEarlierChapterIsSentForward() = runTest {
        val ncx = """
            <ncx><navMap>
              <navPoint><navLabel><text>One</text></navLabel><content src="one.xhtml"/></navPoint>
              <navPoint><navLabel><text>Two</text></navLabel><content src="two.xhtml"/></navPoint>
              <navPoint><navLabel><text>Three</text></navLabel><content src="one.xhtml#later"/></navPoint>
            </navMap></ncx>
        """.trimIndent()

        assertEquals(
            listOf("One" to 0, "Two" to 1, "Three" to 2),
            contentsOf(ncx = ncx).map { it.first to it.second },
        )
    }

    /** Title, section and level of each entry in a three-chapter book. */
    private suspend fun contentsOf(nav: String? = null, ncx: String? = null): List<Triple<String, Int, Int>> {
        val content = epubRead(
            epub(
                manifest = buildString {
                    if (nav != null) {
                        appendLine(
                            """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>""",
                        )
                    }
                    if (ncx != null) {
                        appendLine("""<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>""")
                    }
                    appendLine("""<item id="c1" href="one.xhtml" media-type="application/xhtml+xml"/>""")
                    appendLine("""<item id="c2" href="two.xhtml" media-type="application/xhtml+xml"/>""")
                    appendLine("""<item id="c3" href="three.xhtml" media-type="application/xhtml+xml"/>""")
                },
                spine = """
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                    <itemref idref="c3"/>
                """.trimIndent(),
                entries = buildMap {
                    nav?.let { put("OEBPS/nav.xhtml", it) }
                    ncx?.let { put("OEBPS/toc.ncx", it) }
                    put(
                        "OEBPS/one.xhtml",
                        "<html><body><h1>One</h1><p>First.</p><p id=\"later\">Still first.</p></body></html>",
                    )
                    put("OEBPS/two.xhtml", chapter("Two", "Second."))
                    // No heading, so a file the contents leaves out adds no row of
                    // its own under the chapter before it.
                    put("OEBPS/three.xhtml", "<html><body><p>Third.</p></body></html>")
                },
            ),
        )

        assertNotNull(content)

        return content.outline.orEmpty().map { Triple(it.title, it.pageIndex, it.level) }
    }
}
