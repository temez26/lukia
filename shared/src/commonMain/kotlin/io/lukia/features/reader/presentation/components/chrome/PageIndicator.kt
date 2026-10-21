package io.lukia.features.reader.presentation.components.chrome

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.lukia.features.reader.domain.outline.OutlineEntry
import io.lukia.features.reader.presentation.currentEntryIndex
import io.lukia.features.reader.presentation.percentThrough
import io.lukia.resources.Res
import io.lukia.resources.reader_contents
import io.lukia.resources.reader_open_contents
import io.lukia.resources.reader_page_of_count
import io.lukia.resources.reader_page_x_of_y
import io.lukia.resources.reader_percent
import io.lukia.resources.reader_percent_read
import io.lukia.shared.presentation.components.controls.ShortcutTooltip
import io.lukia.shared.presentation.components.controls.pressable
import io.lukia.shared.presentation.components.icons.DisclosureChevron
import io.lukia.shared.presentation.theme.LocalIconButtonShape
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * Where the reader is in the book, and the way to somewhere else in it.
 *
 * The page number is read inside this composable and nowhere else, so turning a
 * page recomposes this label rather than the bar or the pages around it. The
 * `derivedStateOf` is what keeps that to once per page instead of once per
 * scrolled pixel.
 *
 * It doubles as the contents button: it is already where the eye goes to find
 * out where it is, and costs the toolbar no width to make it the way to move too.
 */
@Composable
internal fun PageIndicatorButton(
    listState: LazyListState,
    pageCount: Int,
    /** The book's chapters, so the bar can say which one the page is in. */
    chapters: List<OutlineEntry>,
    onClick: () -> Unit,
    /** Whether the contents it opens are open, which quietens its tooltip. */
    expanded: Boolean = false,
) {
    // The page and how far through the book it is, as one value read from the
    // list. Kept as two, the share was worked out in this function's body and the
    // page re-read inside the content below - and on iOS only the content was
    // recomposed after a jump with the scrubber, so the bar read "168 / 844 ·
    // 47%" with the share of the page it had come from.
    val position by remember(listState, pageCount) {
        derivedStateOf {
            val page = listState.firstVisibleItemIndex + 1
            BarPosition(page = page, percent = percentThrough(page, pageCount))
        }
    }

    // Named as well as numbered: "page 42 of 380" says how far in, and the
    // chapter says where. Read from the outline rather than carried on the page,
    // because a chapter is a stretch of pages and only the entries know where
    // one stretch ends and the next begins.
    val chapter by remember(chapters) {
        derivedStateOf { chapters.getOrNull(currentEntryIndex(chapters, position.page - 1))?.title }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val metrics = LocalLukiaMetrics.current

    // The icon buttons' shape rather than the rounded rect a control in a layout
    // gets: a capsule where they are round, square where the palette is. This
    // one sits inside the toolbar's own capsule with a couple of points either
    // side of it, and two different corner radii that close together read as a
    // mistake rather than as a hierarchy.
    val shape = LocalIconButtonShape.current

    // Read before the modifier chain: `semantics` is not a composable scope.
    val openContents = stringResource(Res.string.reader_open_contents)
    val place = stringResource(Res.string.reader_page_x_of_y, position.page, pageCount)
    val read = stringResource(Res.string.reader_percent_read, position.percent)

    ShortcutTooltip(
        description = stringResource(Res.string.reader_contents),
        shortcut = "C",
        suppressed = expanded,
    ) {
        // What is pressed, which is as tall as anything else in the bar - a
        // thumb aiming at the middle of the toolbar has to land on this. What is
        // drawn is the row inside it.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = metrics.controlHeight)
                .clip(shape)
                .pressable(
                    interactionSource = interactionSource,
                    shape = shape,
                    onClickLabel = openContents,
                    onClick = onClick,
                )
                // "12 / 348" is a clear enough label to look at and a poor one to
                // hear, so assistive technology is given the sentence instead.
                //
                // Deliberately not a live region: this changes on every page, and
                // announcing each one would talk over a reader scrolling through a
                // long book. The pages themselves carry the position instead.
                .semantics {
                    contentDescription = listOfNotNull(chapter, place, read)
                        .joinToString(separator = ". ")
                },
        ) {
            // No fill of its own: a grey slab in the middle of the bar read as a
            // field to type in. The press feedback says it is a control, and the
            // chevron says what pressing it does.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = Space.md, vertical = Space.xxs),
            ) {
                PageIndicatorLabel(
                    chapter = chapter,
                    position = position,
                    pageCount = pageCount,
                    modifier = Modifier.weight(1f, fill = false),
                )

                DisclosureChevron(
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = Space.sm)
                        .size(metrics.iconSmall * 0.6f),
                )
            }
        }
    }
}

/**
 * What the indicator says: the chapter in view over how far into the book it is,
 * as Apple Books sets its title bar.
 *
 * Two lines rather than one, so the chapter has the bar's width to itself.
 * Sharing a line with the numbers it was cut to "If St…" on a phone, which
 * named nothing; the numbers are the line that never gives way.
 */
@Composable
private fun PageIndicatorLabel(
    chapter: String?,
    position: BarPosition,
    pageCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        if (chapter != null) {
            Text(
                text = chapter,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // The page set as the headline when there is no chapter to name, and
        // under the chapter, smaller, when there is.
        val numbers = if (chapter == null) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.reader_page_of_count, position.page, pageCount),
                style = numbers,
                fontWeight = if (chapter == null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (chapter ==
                    null
                ) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
            )

            Text(
                text = NumbersSeparator,
                style = numbers,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(Res.string.reader_percent, position.percent),
                style = numbers,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** Where the reader is, as the bar states it: the page, and the share of the book. */
@Immutable
private data class BarPosition(val page: Int, val percent: Int)

/** Between the page and the share, as the scrubber sets its own line. */
private const val NumbersSeparator = " · "
