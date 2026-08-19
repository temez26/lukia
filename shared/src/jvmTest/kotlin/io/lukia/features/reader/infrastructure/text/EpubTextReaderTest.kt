package io.lukia.features.reader.infrastructure.text

import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.plainText
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpubTextReaderTest {

    /** The spine is the book's own statement of what order it is read in. */
    @Test
    fun sectionsFollowTheSpineRatherThanTheManifest() {
        val content = readEpub(
            epub(
                manifest = """
                    <item id="c2" href="two.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c1" href="one.xhtml" media-type="application/xhtml+xml"/>
                """.trimIndent(),
                spine = """
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                """.trimIndent(),
                entries = mapOf(
                    "OEBPS/one.xhtml" to chapter("Chapter One", "The first."),
                    "OEBPS/two.xhtml" to chapter("Chapter Two", "The second."),
                ),
            ),
        )

        assertNotNull(content)
        assertEquals(listOf("Chapter One", "Chapter Two"), content.sections.map { it.title })
    }

    @Test
    fun readsTheEpub3NavigationDocumentAsTheTableOfContents() {
        val nav = """
            <html xmlns:epub="http://www.idpf.org/2007/ops">
              <body>
                <nav epub:type="landmarks"><ol><li><a href="one.xhtml">Start</a></li></ol></nav>
                <nav epub:type="toc">
                  <ol>
                    <li><a href="one.xhtml">Chapter One</a>
                      <ol><li><a href="one.xhtml#part2">Part Two</a></li></ol>
                    </li>
                    <li><a href="two.xhtml">Chapter Two</a></li>
                  </ol>
                </nav>
              </body>
            </html>
        """.trimIndent()

        val content = readEpub(
            epub(
                manifest = """
                    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml"
                          properties="nav"/>
                    <item id="c1" href="one.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="two.xhtml" media-type="application/xhtml+xml"/>
                """.trimIndent(),
                spine = """
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                """.trimIndent(),
                entries = mapOf(
                    "OEBPS/nav.xhtml" to nav,
                    "OEBPS/one.xhtml" to chapter("Chapter One", "The first."),
                    "OEBPS/two.xhtml" to chapter("Chapter Two", "The second."),
                ),
            ),
        )

        assertNotNull(content)
        assertEquals(
            listOf(
                Triple("Chapter One", 0, 0),
                Triple("Part Two", 0, 1),
                Triple("Chapter Two", 1, 0),
            ),
            content.outline.orEmpty().map { Triple(it.title, it.pageIndex, it.level) },
        )
    }

    /** Publishers ship an NCX alongside the newer form, and older books only have one. */
    @Test
    fun fallsBackToTheEpub2NcxWhenThereIsNoNavigationDocument() {
        val ncx = """
            <ncx>
              <navMap>
                <navPoint>
                  <navLabel><text>Chapter One</text></navLabel>
                  <content src="one.xhtml"/>
                  <navPoint>
                    <navLabel><text>Part Two</text></navLabel>
                    <content src="one.xhtml#part2"/>
                  </navPoint>
                </navPoint>
                <navPoint>
                  <navLabel><text>Chapter Two</text></navLabel>
                  <content src="two.xhtml"/>
                </navPoint>
              </navMap>
            </ncx>
        """.trimIndent()

        val content = readEpub(
            epub(
                manifest = """
                    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="c1" href="one.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="two.xhtml" media-type="application/xhtml+xml"/>
                """.trimIndent(),
                spine = """
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                """.trimIndent(),
                entries = mapOf(
                    "OEBPS/toc.ncx" to ncx,
                    "OEBPS/one.xhtml" to chapter("Chapter One", "The first."),
                    "OEBPS/two.xhtml" to chapter("Chapter Two", "The second."),
                ),
            ),
        )

        assertNotNull(content)
        assertEquals(
            listOf(
                Triple("Chapter One", 0, 0),
                Triple("Part Two", 0, 1),
                Triple("Chapter Two", 1, 0),
            ),
            content.outline.orEmpty().map { Triple(it.title, it.pageIndex, it.level) },
        )
    }

    /** A flat list of real chapter names beats a panel saying the book has none. */
    @Test
    fun aBookWithNoNavigationGetsAContentsFromItsOwnHeadings() {
        val content = readEpub(
            epub(
                manifest = """
                    <item id="c1" href="one.xhtml" media-type="application/xhtml+xml"/>
                    <item id="c2" href="two.xhtml" media-type="application/xhtml+xml"/>
                """.trimIndent(),
                spine = """
                    <itemref idref="c1"/>
                    <itemref idref="c2"/>
                """.trimIndent(),
                entries = mapOf(
                    "OEBPS/one.xhtml" to chapter("Chapter One", "The first."),
                    "OEBPS/two.xhtml" to chapter("Chapter Two", "The second."),
                ),
            ),
        )

        assertNotNull(content)
        assertEquals(
            listOf("Chapter One", "Chapter Two"),
            content.outline.orEmpty().map { it.title },
        )
    }

    @Test
    fun illustrationsAreResolvedRelativeToTheChapterThatShowsThem() {
        val plate = byteArrayOf(1, 2, 3, 4)

        val content = readEpub(
            epub(
                manifest = """
                    <item id="c1" href="text/one.xhtml" media-type="application/xhtml+xml"/>
                    <item id="p1" href="images/plate.png" media-type="image/png"/>
                """.trimIndent(),
                spine = """<itemref idref="c1"/>""",
                entries = mapOf(
                    "OEBPS/text/one.xhtml" to
                        """<html><body><p>Look:</p><img src="../images/plate.png"/></body></html>""",
                    "OEBPS/images/plate.png" to plate,
                ),
            ),
        )

        assertNotNull(content)

        val figure = content.sections
            .flatMap { it.blocks }
            .filterIsInstance<TextBlock.Figure>()
            .single()

        assertEquals(plate.toList(), figure.bytes.toList())
    }

    /** An href is a URL; the entry it names is not. */
    @Test
    fun percentEncodedHrefsFindTheirEntries() {
        val content = readEpub(
            epub(
                manifest = """
                    <item id="c1" href="Chapter%20One.xhtml" media-type="application/xhtml+xml"/>
                """.trimIndent(),
                spine = """<itemref idref="c1"/>""",
                entries = mapOf("OEBPS/Chapter One.xhtml" to chapter("Chapter One", "Words.")),
            ),
        )

        assertNotNull(content)
        assertEquals("Chapter One", content.sections.single().title)
    }

    @Test
    fun aFileThatIsNotAnEpubIsNotReadAsOne() {
        assertNull(readEpub("not a zip at all".encodeToByteArray()))
        assertNull(readEpub(zip(mapOf("hello.txt" to "hi"))))
    }

    @Test
    fun paragraphsSurviveWithTheirEmphasis() {
        val content = readEpub(
            epub(
                manifest =
                """<item id="c1" href="one.xhtml" media-type="application/xhtml+xml"/>""",
                spine = """<itemref idref="c1"/>""",
                entries = mapOf(
                    "OEBPS/one.xhtml" to
                        "<html><body><p>the <em>Pequod</em> sailed</p></body></html>",
                ),
            ),
        )

        assertNotNull(content)

        val paragraph = content.sections
            .single()
            .blocks
            .filterIsInstance<TextBlock.Paragraph>()
            .single()

        assertEquals("the Pequod sailed", paragraph.text.plainText())
        assertTrue(paragraph.text.any { it.emphasis.isNotEmpty() })
    }

    private fun chapter(title: String, body: String): String =
        "<html><body><h1>$title</h1><p>$body</p></body></html>"

    /**
     * An EPUB with the indirection a real one has: a container naming a package
     * document, and a package document in a sub-directory so that every href in
     * the tests has somewhere to be resolved against.
     */
    private fun epub(
        manifest: String,
        spine: String,
        entries: Map<String, Any>,
    ): ByteArray {
        val container = """
            <?xml version="1.0"?>
            <container xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="OEBPS/content.opf"
                          media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
        """.trimIndent()

        val opf = """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
              <metadata/>
              <manifest>
            $manifest
              </manifest>
              <spine>
            $spine
              </spine>
            </package>
        """.trimIndent()

        return zip(
            mapOf(
                "META-INF/container.xml" to container,
                "OEBPS/content.opf" to opf,
            ) + entries,
        )
    }

    private fun zip(entries: Map<String, Any>): ByteArray {
        val output = ByteArrayOutputStream()

        ZipOutputStream(output).use { stream ->
            entries.forEach { (name, content) ->
                stream.putNextEntry(ZipEntry(name))
                stream.write(
                    when (content) {
                        is ByteArray -> content
                        else -> content.toString().encodeToByteArray()
                    },
                )
                stream.closeEntry()
            }
        }

        return output.toByteArray()
    }
}
