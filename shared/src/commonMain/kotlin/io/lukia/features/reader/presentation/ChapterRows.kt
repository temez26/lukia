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
 * By page rather than by place in the list, because a file's contents are not
 * always in page order: a manual whose closing "Index" pointed back at its
 * first page had every page of the book filed under "Index".
 */
internal fun currentEntryIndex(entries: List<OutlineEntry>, pageIndex: Int): Int {
    var current = -1

    entries.forEachIndexed { index, entry ->
        if (entry.pageIndex > pageIndex) return@forEachIndexed

        if (current == -1 || entry.pageIndex >= entries[current].pageIndex) current = index
    }

    return current
}
