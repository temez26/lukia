package io.lukia.core.infrastructure.epub

import io.lukia.core.domain.BookMetadata
import io.lukia.core.infrastructure.archive.ZipArchiveReader
import io.lukia.core.infrastructure.archive.decodePercentEncoding
import io.lukia.core.infrastructure.archive.resolveArchivePath
import io.lukia.core.infrastructure.xml.XmlEvent
import io.lukia.core.infrastructure.xml.xmlEvents

/** One resource an EPUB declares: a chapter, a stylesheet, an image. */
internal data class EpubManifestItem(
    val id: String,
    /** Resolved against the package document, so it addresses the archive directly. */
    val path: String,
    val mediaType: String,
    /** EPUB 3's `properties`, which is where `cover-image` and `nav` are declared. */
    val properties: String
)

/**
 * An EPUB opened far enough to find things in it.
 *
 * An EPUB is a ZIP with a level of indirection at the front: `container.xml`
 * names a package document, and the package document is what actually lists the
 * book's resources and the order they are read in. Both the library's cover
 * extraction and the reader need to walk that same path, which is why it is
 * here in core rather than in either of them.
 *
 * Every accessor returns null rather than throwing for a book that does not have
 * what was asked for. A malformed EPUB is ordinary input: these files are
 * produced by a long tail of tools, and refusing one outright helps nobody.
 */
internal class EpubPackage private constructor(
    private val zip: ZipArchiveReader,
    /** Kept because the metadata block is read on demand, not at open time. */
    private val packageXml: String,
    val manifest: List<EpubManifestItem>,
    /** Manifest ids in reading order. */
    val spine: List<String>
) {

    private val manifestById: Map<String, EpubManifestItem> = manifest.associateBy { it.id }

    /** The archive entries the spine names, in reading order and skipping any missing. */
    val readingOrder: List<EpubManifestItem>
        get() = spine.mapNotNull(manifestById::get)

    fun read(path: String): ByteArray? = zip.readEntry(path)

    /** What the book says about itself: title, authors, publisher, the rest. */
    fun metadata(): BookMetadata = readEpubMetadata(packageXml)

    /**
     * The cover image, looked for the three ways books declare one: EPUB 3's
     * `cover-image` property, EPUB 2's `<meta name="cover">` pointing at a
     * manifest id, and - for the books that do neither - an image whose id
     * simply says what it is.
     */
    fun coverImagePath(): String? {
        manifest.firstOrNull { "cover-image" in it.properties }
            ?.let { return it.path }

        metaContent(name = "cover")
            ?.let(manifestById::get)
            ?.let { return it.path }

        return manifest.firstOrNull {
            it.id.contains("cover", ignoreCase = true) && it.mediaType.startsWith("image/")
        }?.path
    }

    /** EPUB 3's navigation document, which is a manifest item marked `nav`. */
    val navigationPath: String?
        get() = manifest.firstOrNull { "nav" in it.properties }?.path

    /**
     * EPUB 2's NCX. Still worth reading in an EPUB 3 book: publishers ship both
     * for the sake of older readers, and a file with no `nav` often has a
     * perfectly good NCX sitting beside it.
     */
    val ncxPath: String?
        get() = manifest.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }?.path

    /** Resolves [href], as written inside [fromPath], to an archive entry name. */
    fun resolveFrom(fromPath: String, href: String): String =
        resolveArchivePath(
            baseDirectory = fromPath.substringBeforeLast('/', missingDelimiterValue = ""),
            relativePath = decodePercentEncoding(href)
        )

    private fun metaContent(name: String): String? =
        xmlEvents(packageXml)
            .filterIsInstance<XmlEvent.Open>()
            .firstOrNull { it.name == "meta" && it.attributes["name"] == name }
            ?.attributes
            ?.get("content")

    companion object {

        fun open(epubBytes: ByteArray): EpubPackage? {
            val zip = ZipArchiveReader(epubBytes)

            val packagePath = packageDocumentPath(zip) ?: return null
            val packageXml = zip.readEntry(packagePath)?.decodeToString() ?: return null
            val directory = packagePath.substringBeforeLast('/', missingDelimiterValue = "")

            val manifest = mutableListOf<EpubManifestItem>()
            val spine = mutableListOf<String>()
            var inSpine = false

            xmlEvents(packageXml).forEach { event ->
                when (event) {
                    is XmlEvent.Open -> when (event.name) {
                        "spine" -> inSpine = true

                        "item" -> {
                            val id = event.attributes["id"]
                            val href = event.attributes["href"]

                            if (id != null && href != null) {
                                manifest += EpubManifestItem(
                                    id = id,
                                    path = resolveArchivePath(directory, decodePercentEncoding(href)),
                                    mediaType = event.attributes["media-type"].orEmpty(),
                                    properties = event.attributes["properties"].orEmpty()
                                )
                            }
                        }

                        // Only inside the spine: `itemref` has no meaning elsewhere,
                        // and a manifest is not a reading order.
                        "itemref" -> if (inSpine) {
                            event.attributes["idref"]?.let(spine::add)
                        }
                    }

                    is XmlEvent.Close -> if (event.name == "spine") inSpine = false

                    is XmlEvent.Text -> Unit
                }
            }

            if (manifest.isEmpty()) return null

            return EpubPackage(
                zip = zip,
                packageXml = packageXml,
                manifest = manifest,
                spine = spine
            )
        }

        private fun packageDocumentPath(zip: ZipArchiveReader): String? {
            val container = zip.readEntry("META-INF/container.xml")?.decodeToString() ?: return null

            return xmlEvents(container)
                .filterIsInstance<XmlEvent.Open>()
                .firstOrNull { it.name == "rootfile" }
                ?.attributes
                ?.get("full-path")
                ?.let(::decodePercentEncoding)
        }
    }
}
