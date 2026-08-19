package io.lukia.core.infrastructure.epub

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpubMetadataTest {

    private fun opf(metadata: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="3.0">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
        $metadata
          </metadata>
          <manifest>
            <item id="css" href="style.css" media-type="text/css"/>
          </manifest>
        </package>
    """.trimIndent()

    @Test
    fun readsTheDublinCoreABookDeclares() {
        val metadata = readEpubMetadata(
            opf(
                """
                <dc:title>Moby Dick; or, The Whale</dc:title>
                <dc:creator>Herman Melville</dc:creator>
                <dc:publisher>Harper &amp; Brothers</dc:publisher>
                <dc:date>1851-11-14</dc:date>
                <dc:language>en-US</dc:language>
                """,
            ),
        )

        assertEquals("Moby Dick; or, The Whale", metadata.title)
        assertEquals(listOf("Herman Melville"), metadata.authors)
        assertEquals("Harper & Brothers", metadata.publisher)
        assertEquals("1851-11-14", metadata.published)
        assertEquals("en-US", metadata.language)
    }

    /** Books credit more than one person, and each gets its own element. */
    @Test
    fun collectsEveryCreator() {
        val metadata = readEpubMetadata(
            opf(
                """
                <dc:creator>Ursula K. Le Guin</dc:creator>
                <dc:creator>Brian Attebery</dc:creator>
                """,
            ),
        )

        assertEquals(listOf("Ursula K. Le Guin", "Brian Attebery"), metadata.authors)
    }

    /**
     * `<title>` is Dublin Core inside the metadata block and an ordinary element
     * everywhere else, so a walk that did not stop at `</metadata>` would take a
     * stylesheet's name as the book's.
     */
    @Test
    fun ignoresElementsOutsideTheMetadataBlock() {
        val packageXml = """
            <?xml version="1.0"?>
            <package xmlns="http://www.idpf.org/2007/opf">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:title>The Real Title</dc:title>
              </metadata>
              <manifest>
                <item id="one" href="one.xhtml" media-type="application/xhtml+xml">
                  <title>Not The Title</title>
                </item>
              </manifest>
            </package>
        """.trimIndent()

        assertEquals("The Real Title", readEpubMetadata(packageXml).title)
    }

    /** Publishers put escaped HTML in the blurb; the reader must not be shown tags. */
    @Test
    fun stripsMarkupFromTheDescription() {
        val metadata = readEpubMetadata(
            opf("<dc:description>&lt;p&gt;A &lt;i&gt;whale&lt;/i&gt; of a book.&lt;/p&gt;</dc:description>"),
        )

        assertEquals("A whale of a book.", metadata.description)
    }

    /** Package documents are indented, and the value must not arrive with the indent. */
    @Test
    fun collapsesTheWhitespaceAValueWasWrappedIn() {
        val metadata = readEpubMetadata(
            opf(
                """
                <dc:title>
                    A Very
                    Long Title
                </dc:title>
                """,
            ),
        )

        assertEquals("A Very Long Title", metadata.title)
    }

    /**
     * A book carries several identifiers - a generated UUID, sometimes an ISBN.
     * The ISBN is the one a reader recognises, so it wins however it is declared.
     */
    @Test
    fun prefersTheIsbnOverAGeneratedIdentifier() {
        val metadata = readEpubMetadata(
            opf(
                """
                <dc:identifier id="uuid">urn:uuid:8f1c-4b2e-9a77</dc:identifier>
                <dc:identifier opf:scheme="ISBN">9780142437247</dc:identifier>
                """,
            ),
        )

        assertEquals("9780142437247", metadata.identifier)
    }

    @Test
    fun readsAnIsbnDeclaredAsAUrn() {
        val metadata = readEpubMetadata(
            opf("<dc:identifier>urn:isbn:9780142437247</dc:identifier>"),
        )

        assertEquals("9780142437247", metadata.identifier)
    }

    @Test
    fun fallsBackToWhateverIdentifierThereIs() {
        val metadata = readEpubMetadata(
            opf("<dc:identifier>urn:uuid:8f1c-4b2e-9a77</dc:identifier>"),
        )

        assertEquals("urn:uuid:8f1c-4b2e-9a77", metadata.identifier)
    }

    @Test
    fun collectsSubjectsWithoutRepeatingThem() {
        val metadata = readEpubMetadata(
            opf(
                """
                <dc:subject>Fiction</dc:subject>
                <dc:subject>Sea stories</dc:subject>
                <dc:subject>Fiction</dc:subject>
                """,
            ),
        )

        assertEquals(listOf("Fiction", "Sea stories"), metadata.subjects)
    }

    /** A book that declares nothing must read as nothing rather than as blanks. */
    @Test
    fun aBookThatDeclaresNothingIsEmpty() {
        val metadata = readEpubMetadata(opf(""))

        assertTrue(metadata.isEmpty)
        assertNull(metadata.title)
        assertEquals(emptyList(), metadata.authors)
    }

    /** Empty elements are what a generator writes for a field nobody filled in. */
    @Test
    fun treatsBlankElementsAsAbsent() {
        val metadata = readEpubMetadata(
            opf(
                """
                <dc:title>   </dc:title>
                <dc:publisher></dc:publisher>
                """,
            ),
        )

        assertNull(metadata.title)
        assertNull(metadata.publisher)
    }
}
