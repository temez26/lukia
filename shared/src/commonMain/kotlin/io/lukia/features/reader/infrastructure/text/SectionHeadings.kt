package io.lukia.features.reader.infrastructure.text

import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.TextBlock
import io.lukia.features.reader.domain.TextSection
import io.lukia.features.reader.domain.plainText

/**
 * How far below a chapter derived sub-sections are offered: the shallowest
 * heading level inside it, and the one under that.
 *
 * Two, because that is what a chapter is actually divided into - `2.5` and
 * `2.5.1` - and a third level is a book's own numbering scheme rather than a way
 * to get around it. A contents panel listing every sub-sub-heading is one nobody
 * can find a chapter in.
 */
private const val DERIVED_LEVELS = 2

/**
 * The book's own contents, with each chapter's sub-sections filled in from its
 * headings where the book did not list them.
 *
 * Plenty of books declare a navigation document of eleven chapters and nothing
 * else, while every one of those chapters is divided into a dozen named parts in
 * its own markup. Taking the book at its word there leaves a contents panel that
 * can only reach the openings of chapters, in exactly the books - long technical
 * ones - where getting to a named part matters most.
 *
 * Only where the book said nothing, though. A chapter the contents already
 * describes the inside of is left exactly as it was written: the book's own
 * account of itself is better than one read off its headings, and adding to it
 * would list every section twice.
 */
internal fun List<OutlineEntry>.withSectionHeadings(
    sections: List<TextSection>,
): List<OutlineEntry> {
    if (isEmpty()) return this

    val described = map { it.pageIndex }.toSet()

    return flatMap { entry ->
        // An entry speaks for a chapter only if it is the one thing the contents
        // says about it. Two entries in a section means the book has described
        // the inside of it already.
        if (count { it.pageIndex == entry.pageIndex } > 1) return@flatMap listOf(entry)

        // A long chapter is laid out as several sections, and the ones after the
        // first carry no entry of their own - they are part of the same chapter
        // and their headings belong under the same row.
        val end = (entry.pageIndex + 1 until sections.size).firstOrNull { it in described }
            ?: sections.size

        listOf(entry) + sections.headingsUnder(entry, entry.pageIndex until end)
    }
}

/** One heading found inside a chapter, and where it sits in the book. */
private class FoundHeading(
    val section: Int,
    val block: Int,
    val level: Int,
    val title: String,
)

/**
 * The headings of the sections a chapter covers, as entries under it.
 *
 * Levels are taken relative to the shallowest heading actually found rather than
 * from the tag: a chapter whose parts are `h2` and a chapter whose parts are `h3`
 * are both books divided one level down, and reading the number off the tag would
 * indent one of them for no reason a reader could see.
 */
private fun List<TextSection>.headingsUnder(
    parent: OutlineEntry,
    range: IntRange,
): List<OutlineEntry> {
    val found = range.flatMap { index -> getOrNull(index).headings(index, parent) }
    val top = found.minOfOrNull { it.level } ?: return emptyList()

    return found
        .filter { it.level < top + DERIVED_LEVELS }
        .map { heading ->
            OutlineEntry(
                title = heading.title,
                pageIndex = heading.section,
                level = parent.level + 1 + (heading.level - top),
                blockIndex = heading.block,
            )
        }
}

/**
 * The headings of one section, leaving out the chapter's own title.
 *
 * Two ways of being that title, because books write it both ways. It is
 * anything at or before the block the entry points at - a link into the middle
 * of a file names its own heading - and it is a heading that simply says what
 * the entry already says, which is how a chapter opening with its own name in an
 * `h1` would otherwise appear as a row nested underneath itself.
 */
private fun TextSection?.headings(index: Int, parent: OutlineEntry): List<FoundHeading> =
    this?.blocks?.mapIndexedNotNull { block, content ->
        val heading = content as? TextBlock.Heading
        val title = heading?.text?.plainText()?.trim().orEmpty()

        when {
            title.isEmpty() -> null
            index == parent.pageIndex && block <= parent.blockIndex -> null
            title.equals(parent.title, ignoreCase = true) -> null

            else -> FoundHeading(
                section = index,
                block = block,
                level = requireNotNull(heading).level,
                title = title,
            )
        }
    }.orEmpty()
