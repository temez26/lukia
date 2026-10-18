package io.lukia.features.reader.presentation

import io.lukia.features.reader.domain.outline.OutlineEntry

/**
 * One row of the contents list: an entry, plus what the entries around it say
 * about it.
 *
 * A document's outline arrives flat, carrying only a level per entry, so whether
 * something is a chapter with sections under it is not in the entry but in what
 * follows it. Working that out once here lets the list draw a chapter
 * differently from a section rather than indenting both and hoping the reader
 * counts pixels.
 */
internal data class ChapterRow(
    /** Position in the flat outline, which is what identifies a row to collapse. */
    val index: Int,
    val entry: OutlineEntry,
    val hasChildren: Boolean,
    val isCollapsed: Boolean,
)

/**
 * Where a row stands against the reader: in it, behind them, and how long it is.
 *
 * [progress] is how far through the row's own pages the reader is, set only on
 * the row they are in; [length] is null where it is not known yet.
 */
internal data class ChapterStanding(
    val isCurrent: Boolean = false,
    val isRead: Boolean = false,
    val length: Int? = null,
    val progress: Float? = null,
)

/**
 * The rows to draw for [entries], with the sections under every index in
 * [collapsed] left out.
 *
 * Collapsing hides everything deeper than the collapsed entry up to the next
 * entry at its own level or shallower - so folding a chapter away takes its
 * sub-sections' sub-sections with it, without the outline ever being built into a
 * tree.
 */
internal fun chapterRows(
    entries: List<OutlineEntry>,
    collapsed: Set<Int>,
): List<ChapterRow> {
    val rows = mutableListOf<ChapterRow>()

    // The level being hidden below, while inside a collapsed entry.
    var hiddenBelowLevel: Int? = null

    entries.forEachIndexed { index, entry ->
        val hidden = hiddenBelowLevel?.let { entry.level > it } == true

        if (!hidden) {
            hiddenBelowLevel = null

            val hasChildren = entries.getOrNull(index + 1)
                ?.let { it.level > entry.level } == true
            val isCollapsed = hasChildren && index in collapsed

            if (isCollapsed) hiddenBelowLevel = entry.level

            rows += ChapterRow(
                index = index,
                entry = entry,
                hasChildren = hasChildren,
                isCollapsed = isCollapsed,
            )
        }
    }

    return rows
}

/** The indices that can be collapsed - the entries that have anything under them. */
internal fun collapsibleIndices(entries: List<OutlineEntry>): Set<Int> =
    entries.indices
        .filter { index -> entries.getOrNull(index + 1)?.let { it.level > entries[index].level } == true }
        .toSet()

/**
 * The entry the reader is inside: the one that starts nearest before
 * [pageIndex], and the last of them where several start on that page. `-1` when
 * the reader is ahead of the first entry, which a book with a cover before its
 * first chapter does.
 *
 * An entry not laid out yet is never it: its page is only where the layout has
 * reached, which the reader standing on the last page so far would match.
 *
 * By page rather than by place in the list, because a file's contents are not
 * always in page order: a manual whose closing "Index" pointed back at its
 * first page had every page of the book filed under "Index".
 */
internal fun currentEntryIndex(entries: List<OutlineEntry>, pageIndex: Int): Int {
    var current = -1

    entries.forEachIndexed { index, entry ->
        if (!entry.placed || entry.pageIndex > pageIndex) return@forEachIndexed

        if (current == -1 || entry.pageIndex >= entries[current].pageIndex) current = index
    }

    return current
}

/**
 * How many pages of the chapter the reader is in are left from [pageIndex],
 * counting it: to the end of the top-level chapter, sections and all, as Kindle
 * counts. Counted to the next section instead, a reader at the start of a
 * forty-page chapter was told two pages were left of it.
 *
 * Null before the first entry, where the reader is in no chapter at all, and
 * where that end is not known yet - see [entryLength].
 */
internal fun pagesLeftInEntry(
    entries: List<OutlineEntry>,
    pageIndex: Int,
    pageCount: Int,
    lengthKnown: Boolean,
): Int? {
    val current = currentEntryIndex(entries, pageIndex)
    if (current == -1) return null

    val chapter = (current downTo 0).firstOrNull { entries[it].level == 0 } ?: current
    val length = entryLength(entries, chapter, pageCount, lengthKnown) ?: return null

    return (entries[chapter].pageIndex + length - pageIndex).takeIf { it > 0 }
}

/**
 * How many pages the entry at [index] runs to, sections and all: up to the next
 * entry at its own level or shallower, or the end of the book.
 *
 * Null where that is not known yet, for the same reasons as [pagesLeftInEntry].
 */
internal fun entryLength(
    entries: List<OutlineEntry>,
    index: Int,
    pageCount: Int,
    lengthKnown: Boolean,
): Int? {
    val entry = entries.getOrNull(index)?.takeIf { it.placed } ?: return null
    val next = entries.drop(index + 1).firstOrNull { it.level <= entry.level }

    val end = when {
        next == null -> pageCount.takeIf { lengthKnown && entries.all { it.placed } }
        next.placed -> next.pageIndex
        else -> null
    }

    return end?.let { (it - entry.pageIndex).takeIf { length -> length > 0 } }
}

/**
 * Whether the reader is past the entry at [index] altogether: it ends before the
 * page they are on, and they are not inside it. Entries the reader has not
 * reached, or is still in, are not read.
 */
internal fun isRead(entries: List<OutlineEntry>, index: Int, current: Int): Boolean {
    if (current < 0 || index >= current) return false

    // The chapter holding the reader's section is not behind them.
    val entry = entries[index]
    val inside = (index + 1..current).all { entries[it].level > entry.level }

    return !inside
}
