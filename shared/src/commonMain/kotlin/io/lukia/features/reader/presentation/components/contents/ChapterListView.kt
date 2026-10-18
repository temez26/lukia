package io.lukia.features.reader.presentation.components.contents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.domain.outline.ReaderOutline
import io.lukia.features.reader.presentation.ChapterStanding
import io.lukia.features.reader.presentation.chapterRows
import io.lukia.features.reader.presentation.collapsibleIndices
import io.lukia.features.reader.presentation.currentEntryIndex
import io.lukia.features.reader.presentation.entryLength
import io.lukia.features.reader.presentation.isRead
import io.lukia.resources.Res
import io.lukia.resources.reader_chapters
import io.lukia.resources.reader_chapters_loading
import io.lukia.resources.reader_chapters_none
import io.lukia.resources.reader_chapters_unavailable
import io.lukia.resources.reader_collapse_all
import io.lukia.resources.reader_expand_all
import io.lukia.shared.presentation.components.controls.ButtonSize
import io.lukia.shared.presentation.components.controls.ButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaButton
import io.lukia.shared.presentation.components.surface.SectionLabel
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** How many rows show above the reader's chapter when the list opens on it. */
private const val ROWS_ABOVE_CURRENT = 2

// The book's own table of contents, as rows the reader can walk and open.
// Split from the panel that holds it because the panel is about getting
// somewhere in the book at all - its scrubber does that without any of this.

/**
 * The document's own table of contents.
 *
 * Opens with every chapter folded but the one being read, as book readers do: a
 * technical book's contents run to hundreds of sections, and shown all at once
 * the chapter after this one is a long scroll away. The reader's own chapter
 * stays open, because its sections are the ones they are most likely to want.
 */
@Composable
internal fun Chapters(
    outline: ReaderOutline,
    currentPage: Int,
    pageCount: Int,
    /** False while the book is still being measured and [pageCount] climbs. */
    pageCountFinal: Boolean,
    inset: Dp,
    onOpen: (entryIndex: Int) -> Unit,
    /** The list's own, for the panel to say how much room it may take. */
    modifier: Modifier = Modifier,
) {
    // Outside the branch below, so the heading reads the same whether what
    // follows it is the contents or the reason there are none, and so the
    // folded state is not a piece of remembered state that only some outlines
    // have.
    val entries = (outline as? ReaderOutline.Chapters)?.entries.orEmpty()

    // Keyed on the outline's shape rather than on the entries: a book still
    // being laid out hands over new page numbers every run, and folding keyed on
    // those came undone under the reader's thumb.
    val levels = remember(entries) { entries.map { it.level } }
    val collapsible = remember(levels) { collapsibleIndices(entries) }
    var collapsed by remember(levels) {
        mutableStateOf(collapsible - openAround(entries, currentEntryIndex(entries, currentPage - 1)))
    }

    ChaptersHeader(
        collapseAll = if (collapsible.isEmpty()) null else collapsed != collapsible,
        inset = inset,
        onToggleAll = {
            collapsed = if (collapsed == collapsible) emptySet() else collapsible
        },
    )

    when {
        outline is ReaderOutline.Loading -> ChaptersNotice(stringResource(Res.string.reader_chapters_loading), inset)

        outline is ReaderOutline.Unavailable ->
            ChaptersNotice(stringResource(Res.string.reader_chapters_unavailable), inset)

        entries.isEmpty() -> ChaptersNotice(stringResource(Res.string.reader_chapters_none), inset)

        else -> ChapterList(
            entries = entries,
            collapsed = collapsed,
            foldable = collapsible.isNotEmpty(),
            currentPage = currentPage,
            inset = inset,
            lengthOf = { index -> entryLength(entries, index, pageCount, pageCountFinal) },
            modifier = modifier,
            onToggle = { index ->
                collapsed = if (index in collapsed) collapsed - index else collapsed + index
            },
            onOpen = onOpen,
        )
    }
}

/**
 * How the row for the entry at [index] stands against the reader on [pageIndex].
 * [currentRow] is the row marked as theirs, which a folded chapter can be for a
 * section inside it; [currentIndex] is the entry they are actually in.
 */
private fun List<OutlineEntry>.standingOf(
    index: Int,
    currentRow: Int?,
    currentIndex: Int,
    pageIndex: Int,
    length: Int?,
): ChapterStanding {
    val isCurrent = index == currentRow

    return ChapterStanding(
        isCurrent = isCurrent,
        isRead = isRead(this, index, currentIndex),
        length = length,
        // Counting the page the reader is on, so the last page of a chapter is a
        // full bar rather than one page short of one.
        progress = if (isCurrent && length != null) {
            (pageIndex - this[index].pageIndex + 1).toFloat() / length
        } else {
            null
        },
    )
}

/**
 * The entry at [index] and every entry it sits inside, which are what has to be
 * unfolded for it to be seen. Empty for -1, a reader ahead of the first entry.
 */
private fun openAround(entries: List<OutlineEntry>, index: Int): Set<Int> {
    val open = mutableSetOf<Int>()
    var level = Int.MAX_VALUE

    for (at in index downTo 0) {
        if (at == index || entries[at].level < level) {
            open += at
            level = entries[at].level
        }
        if (level == 0) break
    }

    return open
}

@Composable
private fun ChapterList(
    entries: List<OutlineEntry>,
    collapsed: Set<Int>,
    foldable: Boolean,
    currentPage: Int,
    inset: Dp,
    lengthOf: (entryIndex: Int) -> Int?,
    onToggle: (index: Int) -> Unit,
    onOpen: (entryIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = remember(entries, collapsed) { chapterRows(entries, collapsed) }

    val currentIndex = remember(entries, currentPage) {
        currentEntryIndex(entries, currentPage - 1)
    }

    // The row standing for where the reader is. Normally the entry itself; when
    // that entry has been folded away it is the chapter that swallowed it,
    // which is still the honest answer to "where am I" and keeps the marker on
    // screen.
    val currentRow = remember(rows, currentIndex) {
        rows.lastOrNull { it.index <= currentIndex }
    }

    val listState = rememberLazyListState()

    // Opens on the chapter being read rather than at the top of the book. In a
    // long document the reader is nowhere near page one, and scrolling to find
    // where they already are is the first thing a contents list should save
    // them. A couple of rows above it stay in sight, so it reads as a place in
    // the book rather than the top of a list.
    LaunchedEffect(Unit) {
        val target = rows.indexOfFirst { it.index == currentRow?.index }
        if (target > 0) listState.scrollToItem((target - ROWS_ABOVE_CURRENT).coerceAtLeast(0))
    }

    LazyColumn(state = listState, modifier = modifier) {
        itemsIndexed(items = rows, key = { _, row -> row.index }) { position, row ->
            ChapterRowItem(
                row = row,
                divided = position > 0 && row.entry.level == 0,
                inset = inset,
                foldable = foldable,
                standing = entries.standingOf(
                    row.index,
                    currentRow?.index,
                    currentIndex,
                    currentPage - 1,
                    lengthOf(row.index),
                ),
                onToggle = { onToggle(row.index) },
                onClick = { onOpen(row.index) },
            )
        }
    }
}

/**
 * [collapseAll] is null when the outline has no nesting to fold, which is most
 * documents - there is nothing to offer, so nothing is offered.
 */
@Composable
private fun ChaptersHeader(
    collapseAll: Boolean?,
    inset: Dp,
    onToggleAll: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            // The trailing inset is smaller because the ghost button beside it
            // carries padding of its own; matching them would double it.
            .padding(start = inset, end = inset - Space.md, bottom = Space.xxs),
    ) {
        SectionLabel(text = stringResource(Res.string.reader_chapters))

        if (collapseAll != null) {
            LukiaButton(
                label = stringResource(
                    if (collapseAll) Res.string.reader_collapse_all else Res.string.reader_expand_all,
                ),
                onClick = onToggleAll,
                variant = ButtonVariant.Ghost,
                size = ButtonSize.Compact,
            )
        }
    }
}

@Composable
internal fun ChaptersNotice(message: String, inset: Dp) {
    Text(
        text = message,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = inset, vertical = Space.md),
    )
}
