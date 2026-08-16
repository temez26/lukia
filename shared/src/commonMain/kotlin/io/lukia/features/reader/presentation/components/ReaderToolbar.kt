package io.lukia.features.reader.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.features.reader.domain.ReadingUnit
import io.lukia.shared.presentation.components.BackChevron
import io.lukia.shared.presentation.components.DisclosureChevron
import io.lukia.shared.presentation.components.GlassSurface
import io.lukia.shared.presentation.components.ShortcutTooltip
import io.lukia.shared.presentation.components.scaleOnPress
import io.lukia.shared.presentation.theme.LocalWindowWidthClass
import io.lukia.shared.presentation.theme.MinTouchTarget
import io.lukia.shared.presentation.theme.WindowWidthClass

/** The bar is exactly as tall as the controls in it. */
private val ControlSize: Dp = MinTouchTarget

/** How thick the ring around a keyboard-focused control is. */
private val FocusRing: Dp = 2.dp

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
    endControl: @Composable () -> Unit
) {
    val wide = LocalWindowWidthClass.current != WindowWidthClass.Compact

    GlassSurface(
        shape = RoundedCornerShape(percent = 50),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (wide) Arrangement.Start else Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .height(ControlSize)
        ) {
            val back: @Composable () -> Unit = {
                ShortcutTooltip(description = "Close the book", shortcut = "Esc") {
                    ToolbarButton(onClick = onBack, label = "Close book") {
                        BackChevron(
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
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
                    modifier = Modifier.weight(1f)
                ) {
                    back()

                    if (title != null) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp, end = 12.dp)
                        )
                    }
                }

                centerControl()

                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
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
    unitCount: Int,
    unit: ReadingUnit,
    onClick: () -> Unit
) {
    val current by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex + 1 }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    ShortcutTooltip(description = "Contents", shortcut = "C") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .focusRing(focused, CircleShape)
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClickLabel = "Open contents",
                    onClick = onClick
                )
                .padding(horizontal = 12.dp, vertical = 5.dp)
                // "12 / 348" is a clear enough label to look at and a poor one to
                // hear, so assistive technology is given the sentence instead.
                //
                // Deliberately not a live region: this changes on every page, and
                // announcing each one would talk over a reader scrolling through a
                // long book. The pages themselves carry the position instead.
                .semantics { contentDescription = "${unit.singular} $current of $unitCount" }
        ) {
            Text(
                text = "$current / $unitCount",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            DisclosureChevron(
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 5.dp)
                    .size(9.dp)
            )
        }
    }
}

/**
 * A square, comfortably tappable target for one piece of toolbar chrome.
 *
 * [label] is set as a content description as well as the click label. These
 * buttons draw a glyph and no text, so without the description a screen reader
 * has nothing to announce them by - the click label names the action, not the
 * control it belongs to.
 */
@Composable
internal fun ToolbarButton(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = modifier
            // Scale rather than a ripple: these are bare glyphs on translucent
            // chrome, and a ripple spreading across the glass reads as a smudge.
            .scaleOnPress(interactionSource)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClickLabel = label,
                onClick = onClick
            )
            .size(ControlSize)
            .focusRing(focused, CircleShape)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Marks the control the keyboard is on.
 *
 * These controls draw no ripple and no pressed state of their own, which leaves
 * someone tabbing through the bar with nothing at all to tell them where they
 * are. Focus is the one interaction that has to be visible without a pointer.
 */
@Composable
private fun Modifier.focusRing(focused: Boolean, shape: Shape): Modifier =
    if (focused) border(FocusRing, MaterialTheme.colorScheme.primary, shape) else this
