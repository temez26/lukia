package io.lukia.features.reader.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.features.reader.presentation.ChapterRow
import io.lukia.features.reader.presentation.chapterRows
import io.lukia.features.reader.presentation.collapsibleIndices
import io.lukia.features.reader.presentation.currentEntryIndex
import io.lukia.shared.presentation.components.DisclosureChevron
import io.lukia.shared.presentation.components.DismissOnBack
import io.lukia.shared.presentation.components.GlassSurface
import io.lukia.shared.presentation.components.GlassTone
import io.lukia.shared.presentation.components.popoverTransition
import io.lukia.shared.presentation.components.rememberPopoverProgress
import io.lukia.shared.presentation.theme.LocalWindowWidthClass
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.MinTouchTarget
import io.lukia.shared.presentation.theme.WindowWidthClass
import kotlin.math.roundToInt

/** Grows out of the control it hangs from, matching the other reader panels. */
private val PanelTransformOrigin = TransformOrigin(0.5f, 0f)

private val CompactPanelWidth: Dp = 300.dp

/** A window with room gets a wider panel, so long chapter titles stay on one line. */
private val WidePanelWidth: Dp = 380.dp

private val PanelPadding: Dp = 16.dp

/** Enough for roughly seven chapters before the list takes over the scrolling. */
private val CompactListMaxHeight: Dp = 280.dp

/** A desktop window is tall enough to show a chapter and its sections at once. */
private val WideListMaxHeight: Dp = 400.dp

private val ChapterRowHeight: Dp = MinTouchTarget

/** Indent per level of nesting. One step is the width of the disclosure control. */
private val LevelIndent: Dp = 18.dp

/** The disclosure control, and the space kept for it by rows that have none. */
private val DisclosureSlot: Dp = 26.dp

/** Room for a four-digit page number, so the column of them is straight. */
private val PageNumberWidth: Dp = 34.dp

/** The marker down the start edge of the chapter the reader is in. */
private val CurrentMarkerWidth: Dp = 3.dp

/**
 * Getting somewhere else in the book: a scrubber for the whole document and the
 * document's own table of contents underneath it.
 *
 * Hangs from the page indicator, which is both where the reader looks to find
 * out where they are and the natural place to reach for going somewhere else.
 */
@Composable
internal fun ReaderContentsPanel(
    expanded: Boolean,
    listState: LazyListState,
    pageCount: Int,
    outline: ReaderOutline,
    onGoToPage: (pageIndex: Int) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = rememberPopoverProgress(expanded, label = "readerContentsPanel")

    if (!expanded && progress == 0f) return

    // Read here rather than passed in: the page changes on every scroll, and
    // taking it any higher would recompose the reader around the panel each
    // time. Inside the panel it costs nothing while the panel is closed.
    val currentPage by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }

    DismissOnBack(enabled = expanded, onDismiss = onDismissRequest)

    val wide = LocalWindowWidthClass.current != WindowWidthClass.Compact

    GlassSurface(
        shape = RoundedCornerShape(18.dp),
        tone = GlassTone.Panel,
        modifier = modifier.popoverTransition(progress, PanelTransformOrigin)
    ) {
        Column(
            modifier = Modifier
                .width(if (wide) WidePanelWidth else CompactPanelWidth)
                .padding(vertical = 10.dp)
        ) {
            PageScrubber(
                currentPage = currentPage,
                pageCount = pageCount,
                onGoToPage = onGoToPage
            )

            HorizontalDivider(
                thickness = Dp.Hairline,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = PanelPadding, vertical = 8.dp)
            )

            Chapters(
                outline = outline,
                currentPage = currentPage,
                listMaxHeight = if (wide) WideListMaxHeight else CompactListMaxHeight,
                onGoToPage = {
                    onGoToPage(it)
                    onDismissRequest()
                }
            )
        }
    }
}

/**
 * Drag anywhere in the book.
 *
 * The page only changes when the drag ends. Following the scrubber live would
 * mean rendering every page it passed over, which is exactly the work that makes
 * a scrubber feel heavy in a long document - so the label tracks the thumb and
 * the book waits until it is let go.
 */
@Composable
private fun PageScrubber(
    currentPage: Int,
    pageCount: Int,
    onGoToPage: (pageIndex: Int) -> Unit
) {
    var draggedPage by remember { mutableStateOf<Int?>(null) }

    // Lets go of the drag position once the book has caught up, so the scrubber
    // follows the reader again when they scroll by hand.
    LaunchedEffect(currentPage) { draggedPage = null }

    val shownPage = draggedPage ?: currentPage

    Column(modifier = Modifier.padding(horizontal = PanelPadding)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Page $shownPage of $pageCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // How far in, which the page number alone does not say: page 120 is
            // a different place in a pamphlet than in a doorstop.
            Text(
                text = "${percentThrough(shownPage, pageCount)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (pageCount > 1) {
            Slider(
                value = shownPage.toFloat(),
                valueRange = 1f..pageCount.toFloat(),
                onValueChange = { draggedPage = it.roundToInt() },
                onValueChangeFinished = { draggedPage?.let { onGoToPage(it - 1) } },
                modifier = Modifier.semantics {
                    contentDescription = "Go to page. Page $shownPage of $pageCount"
                }
            )
        }
    }
}

private fun percentThrough(page: Int, pageCount: Int): Int =
    if (pageCount <= 1) 100 else (page * 100f / pageCount).roundToInt()

/**
 * The document's own table of contents.
 *
 * Sections start out shown rather than folded away, so a reader who opens the
 * contents sees everything the book offers. Folding is for afterwards, once a
 * long book's sections are in the way of finding the chapter after this one.
 */
@Composable
private fun Chapters(
    outline: ReaderOutline,
    currentPage: Int,
    listMaxHeight: Dp,
    onGoToPage: (pageIndex: Int) -> Unit
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
        onToggleAll = {
            collapsed = if (collapsed == collapsible) emptySet() else collapsible
        }
    )

    when {
        outline is ReaderOutline.Loading -> ChaptersNotice("Reading chapters…")

        outline is ReaderOutline.Unavailable ->
            ChaptersNotice("Chapters could not be read from this document.")

        entries.isEmpty() -> ChaptersNotice("This document has no chapters.")

        else -> ChapterList(
            entries = entries,
            collapsed = collapsed,
            currentPage = currentPage,
            listMaxHeight = listMaxHeight,
            onToggle = { index ->
                collapsed = if (index in collapsed) collapsed - index else collapsed + index
            },
            onGoToPage = onGoToPage
        )
    }
}

@Composable
private fun ChapterList(
    entries: List<OutlineEntry>,
    collapsed: Set<Int>,
    currentPage: Int,
    listMaxHeight: Dp,
    onToggle: (index: Int) -> Unit,
    onGoToPage: (pageIndex: Int) -> Unit
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
        modifier = Modifier.heightIn(max = listMaxHeight)
    ) {
        items(items = rows, key = { it.index }) { row ->
            ChapterRowItem(
                row = row,
                isCurrent = row.index == currentRow?.index,
                onToggle = { onToggle(row.index) },
                onClick = { onGoToPage(row.entry.pageIndex) }
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
    onToggleAll: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PanelPadding, end = PanelPadding - 8.dp, bottom = 2.dp)
    ) {
        Text(
            text = "Chapters",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (collapseAll != null) {
            val label = if (collapseAll) "Collapse all" else "Expand all"

            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClickLabel = label, onClick = onToggleAll)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .semantics { contentDescription = label }
            )
        }
    }
}

/**
 * One entry in the contents.
 *
 * What makes a chapter a chapter and a section a section is carried three ways
 * over, because indentation alone is not legible in a panel this narrow: a
 * chapter is set in a heavier, larger face, its sections in a lighter and
 * quieter one; every level of nesting adds a hairline guide down the start edge,
 * so a section is visibly hanging off something; and a chapter that has sections
 * carries the control that folds them away, which no section without its own
 * sub-sections does.
 */
@Composable
private fun ChapterRowItem(
    row: ChapterRow,
    isCurrent: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit
) {
    val entry = row.entry
    val isChapter = entry.level == 0
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val markerColor = MaterialTheme.colorScheme.primary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                }
            )
            .drawBehind {
                if (isCurrent) {
                    drawRoundRect(
                        color = markerColor,
                        topLeft = Offset(0f, size.height * 0.18f),
                        size = Size(CurrentMarkerWidth.toPx(), size.height * 0.64f),
                        cornerRadius = CornerRadius(CurrentMarkerWidth.toPx())
                    )
                }

                // One guide per level between this entry and the top, so a
                // sub-section reads as hanging off its section as well as off
                // the chapter above them both.
                repeat(entry.level) { level ->
                    val x = (PanelPadding + LevelIndent * level + DisclosureSlot / 2).toPx()

                    drawLine(
                        color = guideColor,
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClickLabel = "Go to ${entry.title}", onClick = onClick)
            .heightIn(min = ChapterRowHeight)
            .padding(start = PanelPadding + LevelIndent * entry.level, end = PanelPadding)
            .semantics {
                contentDescription = "${entry.title}, page ${entry.pageIndex + 1}"
            }
    ) {
        if (row.hasChildren) {
            DisclosureToggle(
                collapsed = row.isCollapsed,
                title = entry.title,
                onClick = onToggle
            )
        } else {
            // Kept empty rather than closed up, so titles line up down the panel
            // whether or not their row has anything to fold.
            Box(modifier = Modifier.width(DisclosureSlot))
        }

        Text(
            text = entry.title,
            style = if (isChapter) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodySmall
            },
            fontWeight = if (isChapter) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                isCurrent -> MaterialTheme.colorScheme.primary
                isChapter -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        )

        Text(
            text = "${entry.pageIndex + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier
                .padding(start = 8.dp)
                .widthIn(min = PageNumberWidth)
        )
    }
}

/**
 * Folds a chapter's sections away, and says on sight whether they are folded.
 *
 * Its own click target inside the row: pressing the title goes to the chapter,
 * which is what a reader who wants the chapter means, and pressing this shows
 * what is inside it without leaving the panel.
 */
@Composable
private fun DisclosureToggle(
    collapsed: Boolean,
    title: String,
    onClick: () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) -90f else 0f,
        animationSpec = LukiaMotion.gentle(),
        label = "chapterDisclosure"
    )

    val label = if (collapsed) "Show sections of $title" else "Hide sections of $title"

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClickLabel = label, onClick = onClick)
            .size(width = DisclosureSlot, height = ChapterRowHeight)
            .semantics { contentDescription = label }
    ) {
        DisclosureChevron(
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(11.dp)
                .graphicsLayer { rotationZ = rotation }
        )
    }
}

@Composable
private fun ChaptersNotice(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = PanelPadding, vertical = 8.dp)
    )
}
