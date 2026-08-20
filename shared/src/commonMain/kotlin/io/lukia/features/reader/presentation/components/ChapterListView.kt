package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.features.reader.presentation.chapterRows
import io.lukia.features.reader.presentation.collapsibleIndices
import io.lukia.features.reader.presentation.currentEntryIndex
import io.lukia.shared.presentation.components.ButtonSize
import io.lukia.shared.presentation.components.ButtonVariant
import io.lukia.shared.presentation.components.LukiaButton
import io.lukia.shared.presentation.components.SectionLabel
import io.lukia.shared.presentation.theme.Space

// The book's own table of contents, as rows the reader can walk and open.
// Split from the panel that holds it because the panel is about getting
// somewhere in the book at all - its scrubber does that without any of this.

/**
 * The document's own table of contents.
 *
 * Sections start out shown rather than folded away, so a reader who opens the
 * contents sees everything the book offers. Folding is for afterwards, once a
 * long book's sections are in the way of finding the chapter after this one.
 */
@Composable
internal fun Chapters(
    outline: ReaderOutline,
    currentPage: Int,
    inset: Dp,
    listMaxHeight: Dp,
    onGoToPage: (pageIndex: Int) -> Unit,
) {
    // Outside the branch below, so the heading reads the same whether what
    // follows it is the contents or the reason there are none, and so the
    // folded state is not a piece of remembered state that only some outlines
    // have.
    val entries = (outline as? ReaderOutline.Chapters)?.entries.orEmpty()
    var collapsed by remember(entries) { mutableStateOf(emptySet<Int>()) }
    val collapsible = remember(entries) { collapsibleIndices(entries) }

    ChaptersHeader(
        collapseAll = if (collapsible.isEmpty()) null else collapsed != collapsible,
        inset = inset,
        onToggleAll = {
            collapsed = if (collapsed == collapsible) emptySet() else collapsible
        },
    )

    when {
        outline is ReaderOutline.Loading -> ChaptersNotice("Reading chapters…", inset)

        outline is ReaderOutline.Unavailable ->
            ChaptersNotice("Chapters could not be read from this document.", inset)

        entries.isEmpty() -> ChaptersNotice("This document has no chapters.", inset)

        else -> ChapterList(
            entries = entries,
            collapsed = collapsed,
            currentPage = currentPage,
            inset = inset,
            listMaxHeight = listMaxHeight,
            onToggle = { index ->
                collapsed = if (index in collapsed) collapsed - index else collapsed + index
            },
            onGoToPage = onGoToPage,
        )
    }
}

@Composable
private fun ChapterList(
    entries: List<OutlineEntry>,
    collapsed: Set<Int>,
    currentPage: Int,
    inset: Dp,
    listMaxHeight: Dp,
    onToggle: (index: Int) -> Unit,
    onGoToPage: (pageIndex: Int) -> Unit,
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
    // them.
    LaunchedEffect(Unit) {
        val target = rows.indexOfFirst { it.index == currentRow?.index }
        if (target > 0) listState.scrollToItem(target)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.heightIn(max = listMaxHeight),
    ) {
        items(items = rows, key = { it.index }) { row ->
            ChapterRowItem(
                row = row,
                inset = inset,
                isCurrent = row.index == currentRow?.index,
                onToggle = { onToggle(row.index) },
                onClick = { onGoToPage(row.entry.pageIndex) },
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
        SectionLabel(text = "Chapters")

        if (collapseAll != null) {
            LukiaButton(
                label = if (collapseAll) "Collapse all" else "Expand all",
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
