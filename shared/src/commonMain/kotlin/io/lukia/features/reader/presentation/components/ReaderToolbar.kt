package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import io.lukia.features.reader.domain.OutlineEntry
import io.lukia.features.reader.presentation.currentEntryIndex
import io.lukia.shared.presentation.components.BackChevron
import io.lukia.shared.presentation.components.DisclosureChevron
import io.lukia.shared.presentation.components.GlassSurface
import io.lukia.shared.presentation.components.LukiaIconButton
import io.lukia.shared.presentation.components.ShortcutTooltip
import io.lukia.shared.presentation.components.focusRing
import io.lukia.shared.presentation.components.hoverHighlight
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.LukiaAlpha
import io.lukia.shared.presentation.theme.Space
import io.lukia.shared.presentation.theme.isWideWindow

/**
 * How tall the toolbar is, and so how much room the page below it has to leave.
 *
 * A function rather than a constant because the controls in it are sized for
 * whatever is doing the pointing: a bar built for a thumb is half as tall again
 * as one built for a mouse, and the reader would otherwise reserve the wrong
 * amount of room for it on one of the two.
 */
@Composable
internal fun readerToolbarHeight(): Dp = LocalLukiaMetrics.current.iconButton

/**
 * The reader's chrome: leaving the book, where you are in it, and how it reads.
 *
 * A capsule that takes its own row rather than floating over the page. Two
 * reasons: nothing ever passes underneath it, so the page is never partly
 * covered by the thing telling you which page it is; and with no moving content
 * behind it the translucency costs nothing to draw.
 *
 * [GlassSurface] resolves per platform on its own - the translucent, specular
 * treatment on Apple platforms and a flatter elevated surface elsewhere - so the
 * bar is native-looking on iOS without the other platforms being handed an iOS
 * look. See `GlassStyle` for why that is a drawn approximation rather than a
 * system material.
 *
 * [title] is drawn only where there is room for it. A phone puts three controls
 * at arm's length from each other and that is the whole bar; a desktop window is
 * wide enough that the same three, pushed to the extremes, read as a bar with a
 * hole in it - and wide enough to say which book is open, which is what a window
 * on a desktop is expected to do.
 */
@Composable
internal fun ReaderToolbar(
    onBack: () -> Unit,
    title: String?,
    modifier: Modifier = Modifier,
    centerControl: @Composable () -> Unit,
    endControl: @Composable () -> Unit,
) {
    val wide = isWideWindow

    GlassSurface(
        // A capsule, because this is the one piece of chrome in the app that
        // genuinely floats free of everything around it.
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (wide) Arrangement.Start else Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(readerToolbarHeight()),
        ) {
            val back: @Composable () -> Unit = {
                ShortcutTooltip(description = "Close the book", shortcut = "Esc") {
                    LukiaIconButton(label = "Close book", onClick = onBack) { tint, glyph ->
                        BackChevron(tint = tint, modifier = glyph)
                    }
                }
            }

            if (wide) {
                // Three tracks rather than three controls in a row: the outer
                // two share what is left over evenly, which is what keeps the
                // page indicator in the middle of the bar however long the
                // title beside it runs.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    back()

                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = Space.xs, end = Space.lg),
                        )
                    }
                }

                centerControl()

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    endControl()
                }
            } else {
                back()
                centerControl()
                endControl()
            }
        }
    }
}

/**
 * Where the reader is in the book, and the way to somewhere else in it.
 *
 * The page number is read inside this composable and nowhere else, so turning a
 * page recomposes this label rather than the bar or the pages around it. The
 * `derivedStateOf` is what keeps that to once per page instead of once per
 * scrolled pixel.
 *
 * It doubles as the button for the contents panel: it is already where the eye
 * goes to find out where it is in the book, and it costs the toolbar no width to
 * make it also the way to move.
 */
@Composable
internal fun PageIndicatorButton(
    listState: LazyListState,
    pageCount: Int,
    /** The book's chapters, so the bar can say which one the page is in. */
    chapters: List<OutlineEntry>,
    onClick: () -> Unit,
) {
    val current by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }

    // Named as well as numbered: "page 42 of 380" says how far in, and the
    // chapter says where. Read from the outline rather than carried on the page,
    // because a chapter is a stretch of pages and only the entries know where
    // one stretch ends and the next begins.
    val chapter by remember(chapters) {
        derivedStateOf { chapters.getOrNull(currentEntryIndex(chapters, current - 1))?.title }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val metrics = LocalLukiaMetrics.current
    val shape = MaterialTheme.shapes.small

    ShortcutTooltip(description = "Contents", shortcut = "C") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(shape)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = LukiaAlpha.Fill),
                )
                .hoverHighlight(interactionSource, shape)
                .focusRing(interactionSource, shape)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClickLabel = "Open contents",
                    onClick = onClick,
                )
                .padding(horizontal = Space.md, vertical = Space.xs)
                // "12 / 348" is a clear enough label to look at and a poor one to
                // hear, so assistive technology is given the sentence instead.
                //
                // Deliberately not a live region: this changes on every page, and
                // announcing each one would talk over a reader scrolling through a
                // long book. The pages themselves carry the position instead.
                .semantics {
                    contentDescription = listOfNotNull(
                        chapter,
                        "Page $current of $pageCount",
                    ).joinToString(separator = ". ")
                },
        ) {
            PageIndicatorLabel(chapter = chapter, current = current, pageCount = pageCount)

            DisclosureChevron(
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = Space.xs)
                    .size(metrics.iconSmall * 0.6f),
            )
        }
    }
}

/**
 * What the indicator says: the chapter in view, then how far into the book it is.
 *
 * The chapter gives way first. It is the part that can run to a whole line of a
 * title, and the part a reader can do without - the numbers are what the bar is
 * for, and they never shrink.
 */
@Composable
private fun RowScope.PageIndicatorLabel(chapter: String?, current: Int, pageCount: Int) {
    if (chapter != null) {
        Text(
            text = chapter,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(end = Space.xs),
        )

        Text(
            text = "\u00b7",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = Space.xs),
        )
    }

    Text(
        text = "$current / $pageCount",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
