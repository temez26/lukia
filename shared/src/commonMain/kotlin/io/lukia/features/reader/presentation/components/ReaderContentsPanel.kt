package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.ReaderOutline
import io.lukia.shared.presentation.components.DismissOnBack
import io.lukia.shared.presentation.components.GlassSurface
import io.lukia.shared.presentation.components.GlassTone
import io.lukia.shared.presentation.components.LukiaDivider
import io.lukia.shared.presentation.components.popoverTransition
import io.lukia.shared.presentation.components.rememberPopoverProgress
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.isWideWindow
import kotlin.math.roundToInt

/** Grows out of the control it hangs from, matching the other reader panels. */
private val PanelTransformOrigin = TransformOrigin(0.5f, 0f)

private val CompactPanelWidth: Dp = 300.dp

/** A window with room gets a wider panel, so long chapter titles stay on one line. */
private val WidePanelWidth: Dp = 380.dp

/** Enough for roughly seven chapters before the list takes over the scrolling. */
private val CompactListMaxHeight: Dp = 280.dp

/**
 * A desktop window is tall enough to show a chapter and its sections at once,
 * and its rows are shorter, so this is more chapters than the number suggests.
 */
private val WideListMaxHeight: Dp = 440.dp

/** Tall enough to drag, short enough not to be the loudest thing in the panel. */
private val ScrubberHeight: Dp = 28.dp

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
    modifier: Modifier = Modifier,
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

    val wide = isWideWindow
    val inset = LocalLukiaDimens.current.panelPadding

    GlassSurface(
        shape = MaterialTheme.shapes.large,
        tone = GlassTone.Panel,
        modifier = modifier.popoverTransition(progress, PanelTransformOrigin),
    ) {
        Column(
            modifier = Modifier
                .width(if (wide) WidePanelWidth else CompactPanelWidth)
                .padding(vertical = Space.md),
        ) {
            PositionScrubber(
                current = currentPage,
                pageCount = pageCount,
                inset = inset,
                onGoTo = onGoToPage,
            )

            LukiaDivider(
                inset = inset,
                endInset = inset,
                modifier = Modifier.padding(vertical = Space.md),
            )

            Chapters(
                outline = outline,
                currentPage = currentPage,
                inset = inset,
                listMaxHeight = if (wide) WideListMaxHeight else CompactListMaxHeight,
                onGoToPage = {
                    onGoToPage(it)
                    onDismissRequest()
                },
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
private fun PositionScrubber(
    current: Int,
    pageCount: Int,
    inset: Dp,
    onGoTo: (index: Int) -> Unit,
) {
    var dragged by remember { mutableStateOf<Int?>(null) }

    // Lets go of the drag position once the book has caught up, so the scrubber
    // follows the reader again when they scroll by hand.
    LaunchedEffect(current) { dragged = null }

    val shown = dragged ?: current

    Column(modifier = Modifier.padding(horizontal = inset)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "Page $shown of $pageCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // How far in, which the page number alone does not say: page 120 is
            // a different place in a pamphlet than in a doorstop.
            Text(
                text = "${percentThrough(shown, pageCount)}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (pageCount > 1) {
            Slider(
                value = shown.toFloat(),
                valueRange = 1f..pageCount.toFloat(),
                onValueChange = { dragged = it.roundToInt() },
                onValueChangeFinished = { dragged?.let { position -> onGoTo(position - 1) } },
                // Material's own track is a thick expressive bar in its default
                // colours, which is the one control in the app that looked like it
                // came from a different application. Dressed in the panel's own
                // palette and given the height of a scrubber rather than a slider.
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier
                    .height(ScrubberHeight)
                    .semantics {
                        contentDescription =
                            "Go to page. " +
                            "Page $shown of $pageCount"
                    },
            )
        }
    }
}

private fun percentThrough(position: Int, pageCount: Int): Int =
    if (pageCount <= 1) 100 else (position * 100f / pageCount).roundToInt()
