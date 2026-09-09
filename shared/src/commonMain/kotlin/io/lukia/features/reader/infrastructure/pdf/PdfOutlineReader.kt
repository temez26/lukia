package io.lukia.features.reader.infrastructure.pdf

import io.lukia.core.infrastructure.ByteSource
import io.lukia.core.infrastructure.pdf.PdfObjectIndex
import io.lukia.core.infrastructure.pdf.PdfValue
import io.lukia.core.infrastructure.pdf.asArray
import io.lukia.core.infrastructure.pdf.asDictionary
import io.lukia.core.infrastructure.pdf.asName
import io.lukia.core.infrastructure.pdf.asText
import io.lukia.features.reader.domain.outline.OutlineEntry

/**
 * Reads a PDF's table of contents straight out of the file.
 *
 * This exists for Android, whose PDF renderer offers page bitmaps and nothing
 * else - no outline, no destinations. Desktop and iOS get theirs from PDFBox and
 * PDFKit, which already do this and more. Keeping it in common code is what lets
 * it be tested here against real files rather than only on a device.
 *
 * Every step returns nothing rather than failing: a book whose chapter list
 * cannot be read still opens and still turns pages.
 */
internal class PdfOutlineReader(private val source: ByteSource) {

    /**
     * The document's chapters, flattened in reading order. Empty when the
     * document has no outline, null when the file cannot be read at all - the
     * same distinction the reader shows its user.
     */
    fun read(): List<OutlineEntry>? {
        val index = runCatching { PdfObjectIndex(source) }.getOrNull() ?: return null
        if (index.isEncrypted) return null

        val catalog = index.catalog() ?: return null
        val pageNumbers = PageOrder(index).of(catalog) ?: return null

        val outlineRoot = index.resolve(catalog["Outlines"]).asDictionary
            ?: return emptyList()

        val destinations = NamedDestinations(index, catalog)
        val entries = mutableListOf<OutlineEntry>()

        appendItems(
            index = index,
            pageNumbers = pageNumbers,
            destinations = destinations,
            reference = outlineRoot["First"],
            level = 0,
            visited = mutableSetOf(),
            into = entries,
        )

        return entries
    }

    private fun appendItems(
        index: PdfObjectIndex,
        pageNumbers: Map<Int, Int>,
        destinations: NamedDestinations,
        reference: PdfValue?,
        level: Int,
        visited: MutableSet<Int>,
        into: MutableList<OutlineEntry>,
    ) {
        if (level > MAX_DEPTH) return

        var current = reference

        while (into.size < MAX_ENTRIES) {
            val itemReference = current as? PdfValue.Reference ?: return

            // Outlines are doubly linked, and a damaged one can link in a
            // circle. Visiting each item once ends that.
            if (!visited.add(itemReference.objectNumber)) return

            val item = index.resolve(itemReference).asDictionary ?: return

            val title = index.resolve(item["Title"]).asText?.cleanedTitle()
            val pageIndex = pageIndexOf(index, pageNumbers, destinations, item)

            if (!title.isNullOrEmpty() && pageIndex != null) {
                into += OutlineEntry(title = title, pageIndex = pageIndex, level = level)
            }

            // Descended into even when the entry itself led nowhere: a heading
            // with no destination of its own still has real sections under it.
            appendItems(index, pageNumbers, destinations, item["First"], level + 1, visited, into)

            current = item["Next"]
        }
    }

    /**
     * An entry points at a page either directly, through a `GoTo` action, or by
     * a name that the document's own name table resolves. All three are common;
     * which one appears says more about the tool that wrote the file than about
     * the book.
     */
    private fun pageIndexOf(
        index: PdfObjectIndex,
        pageNumbers: Map<Int, Int>,
        destinations: NamedDestinations,
        item: PdfValue.Dictionary,
    ): Int? {
        val destination = item["Dest"]
            ?: index.resolve(item["A"]).asDictionary
                ?.takeIf { index.resolve(it["S"]).asName == "GoTo" }
                ?.get("D")

        return resolveDestination(index, pageNumbers, destinations, destination, hops = 0)
    }

    private fun resolveDestination(
        index: PdfObjectIndex,
        pageNumbers: Map<Int, Int>,
        destinations: NamedDestinations,
        destination: PdfValue?,
        hops: Int,
    ): Int? {
        if (hops > MAX_NAME_HOPS) return null

        return when (
            val resolved = if (destination is PdfValue.Reference) {
                index.resolve(destination)
            } else {
                destination
            }
        ) {
            // [ pageRef /XYZ left top zoom ]
            is PdfValue.Array -> resolved.items.firstOrNull()?.let { target ->
                when (target) {
                    is PdfValue.Reference -> pageNumbers[target.objectNumber]
                    // A plain number is already a page index.
                    is PdfValue.Number -> target.value.toInt().takeIf { it >= 0 }
                    else -> null
                }
            }

            // A named destination, which the name table turns into the array
            // above - or, in older files, a dictionary holding one under /D.
            is PdfValue.Name -> resolveDestination(
                index,
                pageNumbers,
                destinations,
                destinations.lookUp(resolved.value),
                hops + 1,
            )

            is PdfValue.Text -> resolveDestination(
                index,
                pageNumbers,
                destinations,
                destinations.lookUp(resolved.value),
                hops + 1,
            )

            is PdfValue.Dictionary -> resolveDestination(
                index,
                pageNumbers,
                destinations,
                resolved["D"],
                hops + 1,
            )

            else -> null
        }
    }

    private companion object {
        const val MAX_DEPTH = 8
        const val MAX_ENTRIES = 5000
        const val MAX_NAME_HOPS = 4
    }
}

/**
 * Where each page sits in the book, by object number - which is how a
 * destination names the page it points at.
 */
private class PageOrder(private val index: PdfObjectIndex) {

    fun of(catalog: PdfValue.Dictionary): Map<Int, Int>? {
        val root = index.resolve(catalog["Pages"]).asDictionary ?: return null
        val pages = LinkedHashMap<Int, Int>()

        collect(root, depth = 0, visited = mutableSetOf(), into = pages)

        return pages.takeIf { it.isNotEmpty() }
    }

    private fun collect(
        node: PdfValue.Dictionary,
        depth: Int,
        visited: MutableSet<Int>,
        into: LinkedHashMap<Int, Int>,
    ) {
        if (depth > MAX_DEPTH || into.size >= MAX_PAGES) return

        val kids = index.resolve(node["Kids"]).asArray ?: return

        kids.forEach { kid ->
            if (into.size >= MAX_PAGES) return

            val reference = kid as? PdfValue.Reference ?: return@forEach
            if (!visited.add(reference.objectNumber)) return@forEach

            val child = index.resolve(reference).asDictionary ?: return@forEach

            // A node with children is an inner node whatever it calls itself;
            // everything else is a leaf, and leaves are the pages.
            if (child["Kids"] != null) {
                collect(child, depth + 1, visited, into)
            } else {
                into[reference.objectNumber] = into.size
            }
        }
    }

    private companion object {
        const val MAX_DEPTH = 32
        const val MAX_PAGES = 100_000
    }
}

/**
 * The document's named destinations, read on first use.
 *
 * Two formats, both still in circulation: a name tree under `/Names /Dests`, and
 * the older flat dictionary at `/Dests`. Documents written by LaTeX in
 * particular address every chapter this way.
 */
private class NamedDestinations(
    private val index: PdfObjectIndex,
    private val catalog: PdfValue.Dictionary,
) {

    private val byName: Map<String, PdfValue> by lazy { read() }

    fun lookUp(name: String): PdfValue? = byName[name]

    private fun read(): Map<String, PdfValue> {
        val destinations = HashMap<String, PdfValue>()

        index.resolve(catalog["Dests"]).asDictionary?.entries?.forEach { (name, value) ->
            destinations[name] = value
        }

        index.resolve(catalog["Names"]).asDictionary
            ?.let { index.resolve(it["Dests"]).asDictionary }
            ?.let { collectTree(it, depth = 0, into = destinations) }

        return destinations
    }

    private fun collectTree(
        node: PdfValue.Dictionary,
        depth: Int,
        into: HashMap<String, PdfValue>,
    ) {
        if (depth > MAX_DEPTH || into.size >= MAX_NAMES) return

        // A leaf holds alternating name and destination; an inner node holds
        // kids to walk.
        index.resolve(node["Names"]).asArray?.let { names ->
            var position = 0
            while (position + 1 < names.size && into.size < MAX_NAMES) {
                val key = names[position].asText ?: names[position].asName
                if (key != null) into[key] = names[position + 1]
                position += 2
            }
        }

        index.resolve(node["Kids"]).asArray?.forEach { kid ->
            index.resolve(kid).asDictionary?.let { collectTree(it, depth + 1, into) }
        }
    }

    private companion object {
        const val MAX_DEPTH = 16
        const val MAX_NAMES = 50_000
    }
}

/**
 * Titles arrive with whatever whitespace the producing tool left in them, and
 * occasionally with the line breaks of a wrapped heading.
 */
private fun String.cleanedTitle(): String =
    replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
        .split(' ')
        .filter { it.isNotEmpty() }
        .joinToString(" ")
