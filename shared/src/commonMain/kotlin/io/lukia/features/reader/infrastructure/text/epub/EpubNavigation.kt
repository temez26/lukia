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
    val walk = NavigationWalk(placeOf)
    xmlEvents(xml).forEach(walk::on)
    return walk.entries
}

/**
 * Where in the navigation document the walk has got to.
 *
 * A class rather than five locals and a `forEach`, because the state is what the
 * reading is: which `nav` this is, how deep the lists are nested, and whether a
 * link is open. Split out so each event's meaning is a short method rather than a
 * branch four levels inside another one.
 */
private class NavigationWalk(private val placeOf: (href: String) -> OutlinePlace?) {

    val entries = mutableListOf<OutlineEntry>()

    private val label = StringBuilder()
    private var inContents = false
    private var finished = false
    private var listDepth = 0
    private var href: String? = null

    fun on(event: XmlEvent) {
        if (finished) return

        when (event) {
            is XmlEvent.Open -> opened(event)
            is XmlEvent.Close -> closed(event)

            // Only while a link is open: everything else in a navigation
            // document is the punctuation between the links.
            is XmlEvent.Text -> if (inContents && href != null) label.append(event.text)
        }
    }

    private fun opened(event: XmlEvent.Open) {
        when {
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
    }

    private fun closed(event: XmlEvent.Close) {
        when {
            !inContents -> Unit

            event.name == "nav" -> finished = true

            event.name == "ol" || event.name == "ul" -> if (listDepth > 0) listDepth--

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
    }
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
    val walk = NcxWalk(placeOf)
    xmlEvents(xml).forEach(walk::on)
    return walk.entries
}

/** The same idea as [NavigationWalk], over the older format's shape. */
private class NcxWalk(private val placeOf: (href: String) -> OutlinePlace?) {

    val entries = mutableListOf<OutlineEntry>()

    private val label = StringBuilder()
    private var depth = 0
    private var inLabel = false

    fun on(event: XmlEvent) {
        when (event) {
            is XmlEvent.Open -> opened(event)
            is XmlEvent.Close -> closed(event)
            is XmlEvent.Text -> if (inLabel) label.append(event.text)
        }
    }

    private fun opened(event: XmlEvent.Open) {
        when (event.name) {
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
    }

    private fun closed(event: XmlEvent.Close) {
        when (event.name) {
            "navpoint" -> if (depth > 0) depth--
            "text" -> inLabel = false
        }
    }
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
