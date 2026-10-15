package io.lukia.core.infrastructure.epub

import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.infrastructure.ByteSource
import io.lukia.core.infrastructure.archive.ZipArchiveReader
import io.lukia.core.infrastructure.archive.decodePercentEncoding
import io.lukia.core.infrastructure.archive.resolveArchivePath
import io.lukia.core.infrastructure.xml.XmlEvent
import io.lukia.core.infrastructure.xml.xmlEvents

/** One resource an EPUB declares: a chapter, a stylesheet, an image. */
data class EpubManifestItem(
    val id: String,
    /** Resolved against the package document, so it addresses the archive directly. */
    val path: String,
    val mediaType: String,
    /** EPUB 3's `properties`, which is where `cover-image` and `nav` are declared. */
    val properties: String,
)

/**
 * An EPUB opened far enough to find things in it.
 *
 * An EPUB is a ZIP with a level of indirection at the front: `container.xml`
 * names a package document, which lists the book's resources and their reading
 * order. Both the library's cover extraction and the reader walk that path,
 * which is why it is in core rather than in either.
 *
 * Every accessor returns null rather than throwing. A malformed EPUB is ordinary
 * input - these files come from a long tail of tools - and refusing one outright
 * helps nobody.
 */
class EpubPackage private constructor(
    private val zip: ZipArchiveReader,
    /** Kept because the metadata block is read on demand, not at open time. */
    private val packageXml: String,
    val manifest: List<EpubManifestItem>,
    /** Manifest ids in reading order. */
    val spine: List<String>,
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
            relativePath = decodePercentEncoding(href),
        )

    private fun metaContent(name: String): String? =
        xmlEvents(packageXml)
            .filterIsInstance<XmlEvent.Open>()
            .firstOrNull { it.name == "meta" && it.attributes["name"] == name }
            ?.attributes
            ?.get("content")

    companion object {

        fun open(epub: ByteSource): EpubPackage? = open(ZipArchiveReader(epub))

        /** For a book already in memory, and for tests. */
        fun open(epubBytes: ByteArray): EpubPackage? = open(ZipArchiveReader(epubBytes))

        private fun open(zip: ZipArchiveReader): EpubPackage? {
            val document = packageDocument(zip) ?: return null
            val contents = readContents(document.xml, document.directory) ?: return null

            return EpubPackage(
                zip = zip,
                packageXml = document.xml,
                manifest = contents.manifest,
                spine = contents.spine,
            )
        }

        /**
         * The package document, found through the indirection at the front of
         * every EPUB: `container.xml` names it, and only then can it be read.
         */
        private fun packageDocument(zip: ZipArchiveReader): PackageDocument? {
            val path = packageDocumentPath(zip) ?: return null
            val xml = zip.readEntry(path)?.decodeToString() ?: return null

            return PackageDocument(
                xml = xml,
                // Everything the manifest names is written relative to this.
                directory = path.substringBeforeLast('/', missingDelimiterValue = ""),
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

/** The package document itself, and what its paths are written relative to. */
private class PackageDocument(val xml: String, val directory: String)

/** What the package document lists, which is read in one pass over it. */
private class Contents(
    val manifest: List<EpubManifestItem>,
    val spine: List<String>,
)

/**
 * The resources a book declares and the order they are read in.
 *
 * Both come out of the same walk because the spine refers to the manifest by id
 * and the two are interleaved in the document; separating them would mean
 * parsing the package twice to answer one question.
 */
private fun readContents(packageXml: String, directory: String): Contents? {
    val walk = PackageWalk(directory)
    xmlEvents(packageXml).forEach(walk::on)

    // A package that declares no resources is not a book that can be opened,
    // whatever else it says about itself.
    return walk.contents().takeIf { it.manifest.isNotEmpty() }
}

/**
 * What the package document has said so far, and whether the walk is inside the
 * spine - which is the one thing that changes what an element means.
 */
private class PackageWalk(private val directory: String) {

    private val manifest = mutableListOf<EpubManifestItem>()
    private val spine = mutableListOf<String>()
    private var inSpine = false

    fun contents(): Contents = Contents(manifest = manifest, spine = spine)

    fun on(event: XmlEvent) {
        when (event) {
            is XmlEvent.Open -> opened(event)
            is XmlEvent.Close -> if (event.name == "spine") inSpine = false
            is XmlEvent.Text -> Unit
        }
    }

    private fun opened(event: XmlEvent.Open) {
        when (event.name) {
            "spine" -> inSpine = true

            "item" -> manifestItem(event.attributes, directory)?.let { manifest += it }

            // Only inside the spine: `itemref` has no meaning elsewhere, and a
            // manifest is not a reading order.
            "itemref" -> if (inSpine) event.attributes["idref"]?.let(spine::add)
        }
    }
}

/** Null for an `item` missing either of the two things that make it addressable. */
private fun manifestItem(
    attributes: Map<String, String>,
    directory: String,
): EpubManifestItem? {
    val id = attributes["id"] ?: return null
    val href = attributes["href"] ?: return null

    return EpubManifestItem(
        id = id,
        path = resolveArchivePath(directory, decodePercentEncoding(href)),
        mediaType = attributes["media-type"].orEmpty(),
        properties = attributes["properties"].orEmpty(),
    )
}
