package io.lukia.features.reader.infrastructure.text.epub

import io.lukia.core.infrastructure.xml.XmlEvent
import io.lukia.core.infrastructure.xml.decodeEntities
import io.lukia.core.infrastructure.xml.xmlEvents
import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.infrastructure.text.html.collapseWhitespace

/**
 * EPUB 3's navigation document: a `nav` element holding nested ordered lists of
 * links, where the nesting is the depth of the entry.
 *
 * A document may hold several - a table of contents, a list of landmarks, a page
 * list - so the one marked as the contents is read. Only where none is marked is
 * one that says nothing about itself taken instead, which older tools produce:
 * taking the first such list regardless put a page list without a type in front
 * of the real contents.
 */
internal fun navigationEntries(
    xml: String,
    placeOf: (href: String) -> OutlinePlace?,
): List<OutlineEntry> {
    val walk = NavigationWalk(placeOf)
    xmlEvents(xml).forEach(walk::on)

    return walk.lists.firstOrNull { it.kind == NavKind.Contents && it.entries.isNotEmpty() }?.entries
        ?: walk.lists.firstOrNull { it.kind == NavKind.Unmarked && it.entries.isNotEmpty() }?.entries
        ?: emptyList()
}

private enum class NavKind { Contents, Unmarked, Other }

private class NavList(val kind: NavKind) {
    val entries = mutableListOf<OutlineEntry>()
}

/** A list item's label that is not a link, waiting for the entries under it. */
private class Heading(val title: String, val level: Int)

/**
 * Where in the navigation document the walk has got to.
 *
 * A class rather than five locals and a `forEach`, because the state is what the
 * reading is: which `nav` this is, how deep the lists are nested, and whether a
 * link is open. Split out so each event's meaning is a short method rather than a
 * branch four levels inside another one.
 */
private class NavigationWalk(private val placeOf: (href: String) -> OutlinePlace?) {

    val lists = mutableListOf<NavList>()

    private var current: NavList? = null
    private val label = StringBuilder()
    private var listDepth = 0
    private var href: String? = null
    private var inLink = false
    private var inLabel = false

    /**
     * A part title written as a `span` rather than a link, as the spec allows:
     * it has nowhere of its own to go, so it takes the place of its first entry.
     * Dropped, it took the whole part's grouping with it.
     */
    private var heading: Heading? = null

    fun on(event: XmlEvent) {
        when (event) {
            is XmlEvent.Open -> opened(event)
            is XmlEvent.Close -> closed(event)

            // Only inside a label: everything else in a navigation document is
            // the punctuation between the links.
            is XmlEvent.Text -> if (current != null && inLabel) label.append(event.text)
        }
    }

    private fun opened(event: XmlEvent.Open) {
        val list = current

        when {
            event.name == "nav" && list == null -> {
                current = NavList(kindOf(event)).also(lists::add)
                listDepth = 0
                heading = null
            }

            list == null -> Unit

            event.name == "ol" || event.name == "ul" -> listDepth++

            event.name == "a" -> {
                href = event.attributes["href"]
                inLink = true
                inLabel = true
                label.clear()
            }

            event.name == "span" && !inLabel -> {
                inLabel = true
                label.clear()
            }
        }
    }

    private fun closed(event: XmlEvent.Close) {
        val list = current ?: return

        when {
            event.name == "nav" -> current = null

            event.name == "ol" || event.name == "ul" -> if (listDepth > 0) listDepth--

            event.name == "a" && inLink -> {
                val target = href

                if (target == null) waitForEntries() else linked(list, place = placeOf(target))

                href = null
                inLink = false
                inLabel = false
                label.clear()
            }

            event.name == "span" && inLabel && !inLink -> {
                waitForEntries()

                inLabel = false
                label.clear()
            }
        }
    }

    /** A label with nowhere to go: a link without an href is written as often as a `span`. */
    private fun waitForEntries() {
        heading = label.toString().asTitle().takeIf { it.isNotEmpty() }
            ?.let { Heading(it, level = listDepth - 1) }
    }

    private fun linked(list: NavList, place: OutlinePlace?) {
        val level = listDepth - 1
        val waiting = heading

        if (waiting != null && place != null) {
            if (level > waiting.level) list.entries.addEntry(waiting.title, place, waiting.level)
            heading = null
        }

        list.entries.addEntry(title = label.toString(), place = place, level = level)
    }

    /**
     * `epub:type` is a list of words, and `role` is where newer tools say the
     * same thing for the benefit of screen readers.
     */
    private fun kindOf(nav: XmlEvent.Open): NavKind {
        val types = nav.attributes["epub:type"]?.split(' ').orEmpty()
        val role = nav.attributes["role"]

        return when {
            "toc" in types || role == "doc-toc" -> NavKind.Contents
            types.isEmpty() && role == null -> NavKind.Unmarked
            else -> NavKind.Other
        }
    }
}

/**
 * EPUB 2's NCX: `navPoint` elements nested inside one another, each carrying its
 * label and the file it points at as children rather than as attributes.
 *
 * Only the `navMap` is the contents. The page list and any other list after it
 * are built from the same label-and-link pairs, and read as contents they put
 * three hundred rows reading "1", "2", "3" under the last chapter.
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
    private var inMap = false

    fun on(event: XmlEvent) {
        when (event) {
            is XmlEvent.Open -> if (event.name == "navmap") {
                inMap = true
            } else if (inMap) {
                opened(event)
            }
            is XmlEvent.Close -> if (event.name == "navmap") {
                inMap = false
            } else if (inMap) {
                closed(event)
            }
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
    val name = title.asTitle()

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

/**
 * A label as a row's title. Decoded once more because tools escape contents
 * labels twice often enough to matter - see [decodeEntities] - and an `&` that
 * survives the first decoding is never meant to be read as `&#160;`.
 */
private fun String.asTitle(): String = decodeEntities(this).collapseWhitespace().trim()
