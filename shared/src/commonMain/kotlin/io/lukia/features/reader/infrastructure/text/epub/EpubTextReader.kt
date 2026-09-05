package io.lukia.features.reader.infrastructure.text.epub

import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.infrastructure.archive.decodePercentEncoding
import io.lukia.core.infrastructure.epub.EpubManifestItem
import io.lukia.core.infrastructure.epub.EpubPackage
import io.lukia.features.reader.domain.ReflowableContent
import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.domain.text.TextBlock
import io.lukia.features.reader.domain.text.TextSection
import io.lukia.features.reader.domain.text.plainText
import io.lukia.features.reader.infrastructure.text.MAX_BLOCKS_PER_SECTION
import io.lukia.features.reader.infrastructure.text.decodeText
import io.lukia.features.reader.infrastructure.text.html.HtmlContent
import io.lukia.features.reader.infrastructure.text.html.htmlContent
import io.lukia.features.reader.infrastructure.text.sectionStarts
import io.lukia.features.reader.infrastructure.text.withSectionHeadings
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
 * The files are read at the same time as each other and assembled in the order
 * the spine states. Reading one is decompressing it, decoding whatever encoding
 * it was written in, and parsing the markup - the most expensive thing done to a
 * book before it is measured, and a book is dozens of files of it. Nothing in
 * one file depends on another, and the archive they come out of is read-only
 * once it has been indexed, so there is nothing to divide but the work.
 *
 * Assembled in reading order all the same. Which file finished first is a fact
 * about the device; which order they are read in is the book.
 *
 * Read whole before the reader is shown any of it, which was measured against
 * the alternative and is the faster of the two. Handing chapters over as they
 * were parsed, so that the first could be measured while the rest were still
 * being read, sounds like the shorter wait and is not: parsing and measuring
 * both use every core there is, so running them at the same time only takes
 * cores away from the measuring - which is the half the reader is actually
 * waiting on. Two full-speed passes beat two half-speed ones.
 *
 * Returns null when the file is not an EPUB this can make sense of, which the
 * caller turns into a format the reader cannot open rather than an empty book.
 */
internal suspend fun readEpub(
    epubBytes: ByteArray,
    diagnostics: Diagnostics = Diagnostics.None,
): ReflowableContent? {
    val epub = EpubPackage.open(epubBytes) ?: return null

    val sections = mutableListOf<TextSection>()

    /**
     * Where each file of the book ended up, which is what the table of contents
     * needs: it points at files and at places inside them, and a file that
     * turned out to hold nothing gets no section at all.
     */
    val files = mutableMapOf<String, FileSections>()

    val read = coroutineScope {
        epub.readingOrder
            .filter { it.mediaType in ReadableMediaTypes }
            .map { item -> async { item to epub.contentOf(item, diagnostics) } }
            .awaitAll()
    }

    read.forEach { (item, content) ->
        // A file that holds nothing gets no section, which is what the table of
        // contents is told by there being no entry for it in [files].
        if (content == null || content.blocks.isEmpty()) return@forEach

        files[item.path] = sections.append(content)
    }

    if (sections.isEmpty()) return null

    val contents = epub.tableOfContents(files, diagnostics)
        .ifEmpty { sections.headingOutline() }

    return ReflowableContent(
        sections = sections,
        // Filled in from the chapters' own headings wherever the book listed
        // none of its own - see [withSectionHeadings].
        outline = contents.withSectionHeadings(sections),
    )
}

/**
 * One file of the book as blocks, or null where there is nothing readable in it.
 *
 * The images it names are pulled out of the archive as it is parsed, which is
 * why this needs the package and not only the bytes.
 */
private fun EpubPackage.contentOf(
    item: EpubManifestItem,
    diagnostics: Diagnostics,
): HtmlContent? {
    val html = read(item.path)?.let { decodeText(it, diagnostics) } ?: return null

    return htmlContent(html) { source -> read(resolveFrom(item.path, source)) }
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
private fun EpubPackage.tableOfContents(
    files: Map<String, FileSections>,
    diagnostics: Diagnostics,
): List<OutlineEntry> {
    fun placeOf(fromPath: String, href: String): OutlinePlace? =
        files[resolveFrom(fromPath, href)]?.placeOf(href.fragment())

    navigationPath?.let { path ->
        read(path)?.let { decodeText(it, diagnostics) }?.let { xml ->
            val entries = navigationEntries(xml) { href -> placeOf(path, href) }

            if (entries.isNotEmpty()) return entries
        }
    }

    ncxPath?.let { path ->
        read(path)?.let { decodeText(it, diagnostics) }?.let { xml ->
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
