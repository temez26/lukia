package io.lukia.features.reader.infrastructure.text.epub

import io.lukia.core.infrastructure.xml.XmlEvent
import io.lukia.core.infrastructure.xml.xmlEvents
import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.infrastructure.text.html.collapseWhitespace

/**
 * EPUB 3's navigation document: a `nav` element holding nested ordered lists of
 * links, where the nesting is the depth of the entry.
 *
 * A document may hold several - a table of contents, a list of landmarks, a page
 * list - so only the one marked as the contents is read, or the one that says
 * nothing about itself, which older tools produce.
 */
internal fun navigationEntries(
    xml: String,
    placeOf: (href: String) -> OutlinePlace?,
): List<OutlineEntry> {
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
                        place = href?.let(placeOf),
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
internal fun ncxEntries(
    xml: String,
    placeOf: (href: String) -> OutlinePlace?,
): List<OutlineEntry> {
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
                    place = event.attributes["src"]?.let(placeOf),
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

/** Where in the book one table-of-contents link leads. */
internal data class OutlinePlace(val section: Int, val block: Int)

/**
 * Adds an entry, unless it names nothing or points nowhere the reader can go -
 * a link into a chapter that held no text is a row that would do nothing when
 * pressed.
 */
private fun MutableList<OutlineEntry>.addEntry(title: String, place: OutlinePlace?, level: Int) {
    val name = title.collapseWhitespace().trim()

    if (name.isEmpty() || place == null) return

    add(
        OutlineEntry(
            title = name,
            pageIndex = place.section,
            level = level.coerceAtLeast(0),
            blockIndex = place.block,
        ),
    )
}
