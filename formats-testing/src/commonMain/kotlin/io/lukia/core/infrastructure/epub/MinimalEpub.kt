package io.lukia.core.infrastructure.epub

import io.lukia.core.infrastructure.archive.storedZip

/**
 * A complete, valid EPUB written by hand, with a container, a package document
 * and whatever entries a test asks for.
 *
 * Hand-written for [io.lukia.core.infrastructure.pdf.MinimalPdf]'s reason: the
 * archive reader and the package it opens are common code, and until this
 * existed only `jvmTest` ever handed them an archive, through `java.util.zip`.
 * The format the reader meets on iOS - what Calibre and Sigil write - was never
 * read by a test, so a difference between the JDK's zip and everybody else's
 * would have shown up as a book that opens on the desktop and not on a phone.
 *
 * The archive itself is written by [storedZip], which is where the format's own
 * bytes are laid out.
 */
object MinimalEpub {

    const val TITLE = "Moby Dick; or, The Whale"
    const val AUTHOR = "Herman Melville"
    const val FIRST_CHAPTER_TITLE = "Loomings"
    const val SECOND_CHAPTER_TITLE = "The Carpet-Bag"

    const val COVER_PATH = "OEBPS/images/cover.png"
    const val PACKAGE_PATH = "OEBPS/content.opf"

    /** The bytes a cover extractor should come back with, so a test can name them. */
    val coverBytes: ByteArray = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte())

    /** An EPUB 3 book: a `cover-image` property, a nav document, two chapters. */
    fun book(): ByteArray = bookWithCover(coverBytes)

    /**
     * The same book carrying [cover] as its picture, for a test whose subject is
     * the picture rather than the book - what a cover is shrunk to, what a cover
     * too large to store does.
     */
    fun bookWithCover(cover: ByteArray): ByteArray = storedZip(
        mapOf(
            "mimetype" to "application/epub+zip".encodeToByteArray(),
            "META-INF/container.xml" to container().encodeToByteArray(),
            PACKAGE_PATH to packageDocument().encodeToByteArray(),
            COVER_PATH to cover,
            "OEBPS/nav.xhtml" to navigation().encodeToByteArray(),
            "OEBPS/text/one.xhtml" to chapter(FIRST_CHAPTER_TITLE).encodeToByteArray(),
            "OEBPS/text/two.xhtml" to chapter(SECOND_CHAPTER_TITLE).encodeToByteArray(),
        ),
    )

    /**
     * An EPUB 2 book, which declares its cover through `<meta name="cover">`
     * pointing at a manifest id rather than through a property.
     *
     * Still the commonest shape on a shelf of bought books, and a separate
     * branch of `coverImagePath`.
     */
    fun olderBook(): ByteArray = storedZip(
        mapOf(
            "META-INF/container.xml" to container().encodeToByteArray(),
            PACKAGE_PATH to packageDocument(
                version = "2.0",
                coverProperty = "",
                navigationItem = "",
                extraMetadata = """<meta name="cover" content="the-picture"/>""",
            ).encodeToByteArray(),
            COVER_PATH to coverBytes,
            "OEBPS/text/one.xhtml" to chapter(FIRST_CHAPTER_TITLE).encodeToByteArray(),
        ),
    )

    /**
     * A book that declares no cover at all, which is what a plain-text
     * conversion and most public-domain downloads are.
     */
    fun bookWithNoCover(): ByteArray = storedZip(
        mapOf(
            "META-INF/container.xml" to container().encodeToByteArray(),
            PACKAGE_PATH to packageDocument(
                coverProperty = "",
                navigationItem = "",
            ).encodeToByteArray(),
            "OEBPS/text/one.xhtml" to chapter(FIRST_CHAPTER_TITLE).encodeToByteArray(),
        ),
    )

    private fun container(packagePath: String = PACKAGE_PATH): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="$packagePath" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    private fun packageDocument(
        version: String = "3.0",
        coverProperty: String = """ properties="cover-image"""",
        navigationItem: String = NavigationItem,
        extraMetadata: String = "",
    ): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <package xmlns="http://www.idpf.org/2007/opf" version="$version">
          <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
            <dc:title>$TITLE</dc:title>
            <dc:creator>$AUTHOR</dc:creator>
            <dc:language>en</dc:language>
            <dc:identifier>urn:isbn:9780142437247</dc:identifier>
            $extraMetadata
          </metadata>
          <manifest>
            <item id="the-picture" href="images/cover.png" media-type="image/png"$coverProperty/>
            $navigationItem
            <item id="one" href="text/one.xhtml" media-type="application/xhtml+xml"/>
            <item id="two" href="text/two.xhtml" media-type="application/xhtml+xml"/>
          </manifest>
          <spine>
            <itemref idref="one"/>
            <itemref idref="two"/>
          </spine>
        </package>
    """.trimIndent()

    private fun navigation(): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <html xmlns:epub="http://www.idpf.org/2007/ops">
          <body>
            <nav epub:type="toc">
              <ol>
                <li><a href="text/one.xhtml">$FIRST_CHAPTER_TITLE</a></li>
                <li><a href="text/two.xhtml">$SECOND_CHAPTER_TITLE</a></li>
              </ol>
            </nav>
          </body>
        </html>
    """.trimIndent()

    private fun chapter(title: String): String =
        "<html><body><h1>$title</h1><p>Call me Ishmael.</p></body></html>"

    /** Only EPUB 3 has one; an EPUB 2 book carries an NCX or nothing. */
    private const val NavigationItem =
        """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>"""
}
