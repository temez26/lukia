package io.lukia.features.reader.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.shared.presentation.components.GlassSurface
import kotlin.math.roundToInt

/** Grows out of the control it hangs from, matching the other reader panels. */
private val PanelTransformOrigin = TransformOrigin(0.5f, 0f)

private val PanelWidth: Dp = 300.dp
private val PanelPadding: Dp = 16.dp

/** Enough for roughly seven chapters before the list takes over the scrolling. */
private val ChapterListMaxHeight: Dp = 280.dp

/** The smallest a row can be and still be hit accurately with a thumb. */
private val ChapterRowHeight: Dp = 44.dp

/** Indent per level of nesting, kept small so deep sections stay readable. */
private val LevelIndent: Dp = 14.dp

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
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = if (expanded) 180 else 140),
        label = "readerContentsPanel"
    )

    if (!expanded && progress == 0f) return


    // Read here rather than passed in: the page changes on every scroll, and
    // taking it any higher would recompose the reader around the panel each
    // time. Inside the panel it costs nothing while the panel is closed.
    val currentPage by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }

    DismissOnBack(enabled = expanded, onDismiss = onDismissRequest)

    GlassSurface(
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.graphicsLayer {
            alpha = progress
            val scale = 0.92f + 0.08f * progress
            scaleX = scale
            scaleY = scale
            transformOrigin = PanelTransformOrigin
        }
    ) {
        Column(
            modifier = Modifier
                .width(PanelWidth)
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
        Text(
            text = "Page $shownPage of $pageCount",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

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

@Composable
private fun Chapters(
    outline: ReaderOutline,
    currentPage: Int,
    onGoToPage: (pageIndex: Int) -> Unit
) {
    Text(
        text = "Chapters",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = PanelPadding, end = PanelPadding, bottom = 4.dp)
    )

    when (outline) {
        ReaderOutline.Loading -> ChaptersNotice("Reading chapters…")

        ReaderOutline.Unavailable ->
            ChaptersNotice("Chapters could not be read from this document.")

        is ReaderOutline.Chapters -> {
            val entries = outline.entries

            if (entries.isEmpty()) {
                ChaptersNotice("This document has no chapters.")
                return
            }

            // The entry the reader is inside: the last one that starts at or
            // before the current page.
            val currentEntry = remember(entries, currentPage) {
                entries.lastOrNull { it.pageIndex <= currentPage - 1 }
            }

            LazyColumn(modifier = Modifier.heightIn(max = ChapterListMaxHeight)) {
                items(items = entries) { entry ->
                    ChapterRow(
                        entry = entry,
                        isCurrent = entry == currentEntry,
                        onClick = { onGoToPage(entry.pageIndex) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChapterRow(
    entry: OutlineEntry,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    Color.Transparent
                }
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClickLabel = "Go to ${entry.title}", onClick = onClick)
            .heightIn(min = ChapterRowHeight)
            .padding(
                start = PanelPadding + LevelIndent * entry.level,
                end = PanelPadding,
                top = 8.dp,
                bottom = 8.dp
            )
            .semantics {
                contentDescription = "${entry.title}, page ${entry.pageIndex + 1}"
            }
    ) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            // Nested sections read as subordinate without needing a second style.
            fontWeight = if (entry.level == 0) FontWeight.Medium else FontWeight.Normal,
            color = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )

        Text(
            text = "${entry.pageIndex + 1}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp)
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

/**
 * Back on Android, Escape on desktop.
 */
@Suppress("DEPRECATION")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DismissOnBack(
    enabled: Boolean,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = enabled, onBack = onDismiss)
}
