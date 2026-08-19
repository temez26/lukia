package io.lukia.features.reader.infrastructure.text

import io.lukia.core.infrastructure.epub.EpubPackage
import io.lukia.core.infrastructure.xml.XmlEvent
import io.lukia.core.infrastructure.xml.xmlEvents
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextSection
import io.lukia.features.reader.domain.plainText

/**
 * The media types a spine item has to be for there to be anything to read in it.
 * An empty type is accepted because plenty of books simply do not declare one.
 */
private val ReadableMediaTypes = setOf(
    "",
    "application/xhtml+xml",
    "text/html",
    "application/x-dtbook+xml",
)

/**
 * Reads an EPUB into the sections the reader shows, in the order the book says
 * they are read in.
 *
 * The spine is taken as given: one spine item becomes one section, even where
 * that means a section holding three lines of front matter or a chapter running
 * to forty pages. A book states its own divisions, and re-dividing them by
 * looking at the headings would be overruling the person who made it - the
 * reader would open at "chapter 4" and find itself somewhere the book never
 * said was a place.
 *
 * Returns null when the file is not an EPUB this can make sense of, which the
 * caller turns into a format the reader cannot open rather than an empty book.
 */
internal fun readEpub(epubBytes: ByteArray): ReflowableContent? {
    val epub = EpubPackage.open(epubBytes) ?: return null

    val sections = mutableListOf<TextSection>()

    /**
     * Where each chapter ended up, which is what the table of contents needs:
     * it points at files, and a chapter that turned out to hold nothing gets no
     * section, so the two lists cannot be assumed to line up.
     */
    val sectionByPath = mutableMapOf<String, Int>()

    epub.readingOrder
        .filter { it.mediaType in ReadableMediaTypes }
        .forEach { item ->
            val html = epub.read(item.path)?.let(::decodeText) ?: return@forEach

            val blocks = htmlBlocks(html) { source ->
                epub.read(epub.resolveFrom(item.path, source))
            }

            if (blocks.isEmpty()) return@forEach

            sectionByPath[item.path] = sections.size
            sections += TextSection(title = blocks.firstHeading(), blocks = blocks)
        }

    if (sections.isEmpty()) return null

    return ReflowableContent(
        sections = sections,
        outline = epub.tableOfContents(sectionByPath).ifEmpty { sections.headingOutline() },
    )
}

/** What a chapter calls itself, taken from the first heading it opens with. */
private fun List<TextBlock>.firstHeading(): String? =
    filterIsInstance<TextBlock.Heading>()
        .firstOrNull()
        ?.text
        ?.plainText()
        ?.trim()
        ?.ifEmpty { null }

/**
 * A table of contents assembled from what the chapters call themselves.
 *
 * The fallback for a book that ships no navigation of its own, which is common
 * in older and hand-made EPUBs. Flat, with no nesting, because chapter headings
 * are all this can see - but a flat list of real chapter names is worth far more
 * to someone trying to get around a book than the panel saying it has none.
 */
private fun List<TextSection>.headingOutline(): List<OutlineEntry> =
    mapIndexedNotNull { index, section ->
        section.title?.let { OutlineEntry(title = it, pageIndex = index, level = 0) }
    }

/**
 * The book's own table of contents, from whichever of the two forms it carries.
 *
 * EPUB 3's navigation document is tried first and EPUB 2's NCX second. Both are
 * tried whatever version the book claims to be: publishers ship an NCX alongside
 * the newer form for the sake of older devices, and a book with an empty or
 * malformed `nav` very often has a perfectly good NCX sitting beside it.
 */
private fun EpubPackage.tableOfContents(sectionByPath: Map<String, Int>): List<OutlineEntry> {
    navigationPath?.let { path ->
        read(path)?.let(::decodeText)?.let { xml ->
            val entries = navigationEntries(xml) { href ->
                sectionByPath[resolveFrom(path, href)]
            }

            if (entries.isNotEmpty()) return entries
        }
    }

    ncxPath?.let { path ->
        read(path)?.let(::decodeText)?.let { xml ->
            return ncxEntries(xml) { href -> sectionByPath[resolveFrom(path, href)] }
        }
    }

    return emptyList()
}

/**
 * EPUB 3's navigation document: a `nav` element holding nested ordered lists of
 * links, where the nesting is the depth of the entry.
 *
 * A document may hold several - a table of contents, a list of landmarks, a page
 * list - so only the one marked as the contents is read, or the one that says
 * nothing about itself, which older tools produce.
 */
private fun navigationEntries(xml: String, sectionOf: (String) -> Int?): List<OutlineEntry> {
    val entries = mutableListOf<OutlineEntry>()
    val label = StringBuilder()

    var inContents = false
    var finished = false
    var listDepth = 0
    var href: String? = null

    xmlEvents(xml).forEach { event ->
        if (finished) return@forEach

        when (event) {
            is XmlEvent.Open -> when {
                event.name == "nav" && !inContents -> {
                    val type = event.attributes["epub:type"]
                    inContents = type == null || "toc" in type
                }

                !inContents -> Unit

                event.name == "ol" || event.name == "ul" -> listDepth++

                event.name == "a" -> {
                    href = event.attributes["href"]
                    label.clear()
                }
            }

            is XmlEvent.Close -> when {
                !inContents -> Unit

                event.name == "nav" -> finished = true

                event.name == "ol" || event.name == "ul" ->
                    if (listDepth > 0) listDepth--

                event.name == "a" -> {
                    entries.addEntry(
                        title = label.toString(),
                        section = href?.let(sectionOf),
                        level = listDepth - 1,
                    )

                    href = null
                    label.clear()
                }
            }

            // Only while a link is open: everything else in a navigation
            // document is the punctuation between the links.
            is XmlEvent.Text -> if (inContents && href != null) label.append(event.text)
        }
    }

    return entries
}

/**
 * EPUB 2's NCX: `navPoint` elements nested inside one another, each carrying its
 * label and the file it points at as children rather than as attributes.
 *
 * The entry is emitted on the `content` element because that is the one that
 * says where the entry goes, and it always follows the label it belongs to.
 */
private fun ncxEntries(xml: String, sectionOf: (String) -> Int?): List<OutlineEntry> {
    val entries = mutableListOf<OutlineEntry>()
    val label = StringBuilder()

    var depth = 0
    var inLabel = false

    xmlEvents(xml).forEach { event ->
        when (event) {
            is XmlEvent.Open -> when (event.name) {
                "navpoint" -> {
                    depth++
                    label.clear()
                }

                "text" -> inLabel = true

                "content" -> entries.addEntry(
                    title = label.toString(),
                    section = event.attributes["src"]?.let(sectionOf),
                    level = depth - 1,
                )
            }

            is XmlEvent.Close -> when (event.name) {
                "navpoint" -> if (depth > 0) depth--
                "text" -> inLabel = false
            }

            is XmlEvent.Text -> if (inLabel) label.append(event.text)
        }
    }

    return entries
}

/**
 * Adds an entry, unless it names nothing or points nowhere the reader can go -
 * a link into a chapter that held no text is a row that would do nothing when
 * pressed.
 */
private fun MutableList<OutlineEntry>.addEntry(title: String, section: Int?, level: Int) {
    val name = title.collapseWhitespace().trim()

    if (name.isEmpty() || section == null) return

    add(OutlineEntry(title = name, pageIndex = section, level = level.coerceAtLeast(0)))
}
