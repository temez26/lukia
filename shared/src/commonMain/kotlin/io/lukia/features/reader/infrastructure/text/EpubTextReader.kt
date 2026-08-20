package io.lukia.features.reader.infrastructure.text

import io.lukia.core.infrastructure.archive.decodePercentEncoding
import io.lukia.core.infrastructure.epub.EpubPackage
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
 * The spine is taken as given wherever the book has divided itself: one spine
 * item becomes one section, even where that means a section holding three lines
 * of front matter. A book states its own divisions, and re-dividing them by
 * looking at the headings would be overruling the person who made it - the
 * reader would open at "chapter 4" and find itself somewhere the book never
 * said was a place.
 *
 * A book that has not divided itself is the case that rule cannot cover. Tools
 * like Leanpub's write every chapter of a book into one file and address them by
 * anchor, and taking the spine at its word there gives a reader four sections,
 * one of which is the entire book - no working scrubber, no usable contents, and
 * a section too large to lay out smoothly. Such a file is divided by
 * [sectionStarts], the same rule every undivided format gets, and its anchors
 * are what put the table of contents back on the right sections.
 *
 * Returns null when the file is not an EPUB this can make sense of, which the
 * caller turns into a format the reader cannot open rather than an empty book.
 */
internal fun readEpub(epubBytes: ByteArray): ReflowableContent? {
    val epub = EpubPackage.open(epubBytes) ?: return null

    val sections = mutableListOf<TextSection>()

    /**
     * Where each file of the book ended up, which is what the table of contents
     * needs: it points at files and at places inside them, and a file that
     * turned out to hold nothing gets no section at all.
     */
    val files = mutableMapOf<String, FileSections>()

    epub.readingOrder
        .filter { it.mediaType in ReadableMediaTypes }
        .forEach { item ->
            val html = epub.read(item.path)?.let(::decodeText) ?: return@forEach

            val content = htmlContent(html) { source ->
                epub.read(epub.resolveFrom(item.path, source))
            }

            if (content.blocks.isEmpty()) return@forEach

            files[item.path] = sections.append(content)
        }

    if (sections.isEmpty()) return null

    return ReflowableContent(
        sections = sections,
        outline = epub.tableOfContents(files).ifEmpty { sections.headingOutline() },
    )
}

/** Where one file of the book ended up once it was laid out as sections. */
private class FileSections(
    private val firstSection: Int,
    private val anchorPlaces: Map<String, OutlinePlace>,
) {

    /**
     * Where a link into this file leads. An unknown anchor falls back to the top
     * of the file rather than nowhere: a book whose contents point at ids it
     * does not contain is still a book worth getting around.
     */
    fun placeOf(fragment: String): OutlinePlace =
        anchorPlaces[fragment] ?: OutlinePlace(section = firstSection, block = 0)
}

/** Lays [content] out as sections at the end of the book so far. */
private fun MutableList<TextSection>.append(content: HtmlContent): FileSections {
    val firstSection = size
    val starts = content.divisions()

    starts.forEachIndexed { index, start ->
        val end = starts.getOrElse(index + 1) { content.blocks.size }
        val blocks = content.blocks.subList(start, end).toList()

        add(TextSection(title = blocks.firstHeading(), blocks = blocks))
    }

    return FileSections(
        firstSection = firstSection,
        // A section's blocks are numbered from its own start, so an anchor's
        // place in the file has to be moved onto the section that took it -
        // otherwise every link past the first section would point beyond the
        // end of the one it landed in.
        anchorPlaces = content.anchors.mapValues { (_, block) ->
            val start = starts.indexOfLast { it <= block }

            OutlinePlace(section = firstSection + start, block = block - starts[start])
        },
    )
}

/**
 * Where this file divides into sections: nowhere at all, which is the spine's
 * own answer, unless the file is too large for that answer to be usable.
 */
private fun HtmlContent.divisions(): List<Int> =
    if (blocks.size <= MAX_BLOCKS_PER_SECTION) listOf(0) else sectionStarts(blocks)

/** What a section calls itself, taken from the first heading it opens with. */
private fun List<TextBlock>.firstHeading(): String? =
    filterIsInstance<TextBlock.Heading>()
        .firstOrNull()
        ?.text
        ?.plainText()
        ?.trim()
        ?.ifEmpty { null }

/**
 * A table of contents assembled from what the sections call themselves.
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
private fun EpubPackage.tableOfContents(files: Map<String, FileSections>): List<OutlineEntry> {
    fun placeOf(fromPath: String, href: String): OutlinePlace? =
        files[resolveFrom(fromPath, href)]?.placeOf(href.fragment())

    navigationPath?.let { path ->
        read(path)?.let(::decodeText)?.let { xml ->
            val entries = navigationEntries(xml) { href -> placeOf(path, href) }

            if (entries.isNotEmpty()) return entries
        }
    }

    ncxPath?.let { path ->
        read(path)?.let(::decodeText)?.let { xml ->
            return ncxEntries(xml) { href -> placeOf(path, href) }
        }
    }

    return emptyList()
}

/**
 * The id an href points at, empty where it points at the file as a whole.
 * Percent-decoded for the same reason the path is: an href is a URL and the id
 * it names is not.
 */
private fun String.fragment(): String = decodePercentEncoding(substringAfter('#', ""))
