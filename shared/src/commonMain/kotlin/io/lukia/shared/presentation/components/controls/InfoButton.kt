package io.lukia.shared.presentation.components.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.lukia.shared.presentation.components.icons.InfoGlyph
import io.lukia.shared.presentation.components.menu.DismissOnBack
import io.lukia.shared.presentation.components.menu.popoverTransition
import io.lukia.shared.presentation.components.menu.rememberPopoverProgress
import io.lukia.shared.presentation.components.surface.GlassSurface
import io.lukia.shared.presentation.components.surface.GlassTone
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.Space

/** The gap between the mark and the card that hangs off it. */
private val AnchorGap = Space.sm

/**
 * The least room left between the card and the edge of the window.
 *
 * A mark near the start of a row cannot have the card centred under it without
 * pushing it off screen, so it gets clamped - and clamped to nothing it would sit
 * flush against the window, which reads as the card having been cut off rather
 * than moved.
 */
private val WindowMargin = Space.lg

/**
 * How wide the card is allowed to get.
 *
 * A measure rather than a fraction of the window: this holds a paragraph, and a
 * paragraph set the full width of a desktop window is a line nobody's eye can
 * carry back. Narrow enough to read, wide enough that three sentences are not a
 * column of two words.
 */
private val InfoWidth: Dp = 288.dp

/**
 * How tall it is allowed to get before it scrolls inside itself.
 *
 * Everything here is prose somebody asked to see, so nothing is ever cut off -
 * but a licence list is longer than a phone, and a card that grew to hold it
 * would be a screen with no way out of it.
 */
private val InfoMaxHeight: Dp = 360.dp

/**
 * The mark beside a setting that explains it, and the card it opens.
 *
 * Settings on this app's pages used to carry their explanation as prose under
 * every group and under every switch - true, but a page somebody opened to
 * change one thing read as an essay about all of them. The words are the same
 * words; what changed is that they arrive when they are asked for.
 *
 * [label] is what a screen reader announces the mark as, so it should name the
 * setting rather than the mark - "About Motion", not "Help".
 *
 * State is held here rather than by the caller. There is nothing a page can do
 * with "the explanation of Motion is open", and a caller that had to remember it
 * would need one flag per setting on a screen that has a dozen.
 */
@Composable
fun InfoButton(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Saveable, so an explanation stays open across a rotation. It is exactly
    // the moment somebody is reading it.
    var open by rememberSaveable { mutableStateOf(false) }

    val metrics = LocalLukiaMetrics.current

    Box(modifier = modifier) {
        LukiaIconButton(
            label = label,
            onClick = { open = !open },
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            size = metrics.iconButtonSmall,
            glyphSize = metrics.iconSmall,
        ) { tint, glyph ->
            InfoGlyph(tint = tint, modifier = glyph)
        }

        // Inside the box, so what the card hangs from is the mark rather than
        // whatever row the caller put it in.
        InfoPopover(expanded = open, onDismissRequest = { open = false }, content = content)
    }
}

/** The common case: one passage of prose, with nothing to press inside it. */
@Composable
fun InfoButton(
    label: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    InfoButton(label = label, modifier = modifier) {
        InfoProse(text = text)
    }
}

/** A passage inside an [InfoButton] card, set the way every other one is. */
@Composable
fun InfoProse(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/**
 * The card itself: the app's glass, under the mark, clamped into the window.
 *
 * Focusable, unlike the app's menus. A menu is dismissed by a scrim the screen
 * behind it puts up, because a long press has to be able to finish landing on
 * the page; this opens on a plain tap and has nothing behind it that wants the
 * next one, so it takes focus and a press anywhere outside simply closes it.
 */
@Composable
private fun InfoPopover(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress = rememberPopoverProgress(expanded, label = "infoPopover")

    // Held in the tree until the exit has finished drawing - see the menus.
    if (!expanded && progress == 0f) return

    DismissOnBack(enabled = expanded, onDismiss = onDismissRequest)

    val density = LocalDensity.current
    val gapPx = with(density) { AnchorGap.roundToPx() }
    val marginPx = with(density) { WindowMargin.roundToPx() }
    val positionProvider = remember(gapPx, marginPx) {
        InfoPopoverPositionProvider(gapPx = gapPx, marginPx = marginPx)
    }
    val scroll = rememberScrollState()

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        GlassSurface(
            shape = MaterialTheme.shapes.medium,
            tone = GlassTone.Panel,
            modifier = Modifier.popoverTransition(
                progress = progress,
                // Grows out of the mark, which the provider centres it under.
                transformOrigin = TransformOrigin(0.5f, 0f),
            ),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = InfoWidth)
                    .heightIn(max = InfoMaxHeight)
                    .verticalScroll(scroll)
                    .padding(Space.lg),
                content = content,
            )
        }
    }
}

/**
 * Centres the card under its mark, then clamps it into the window.
 *
 * Centred rather than edge-aligned because the mark sits in the middle of a row
 * as often as at its end, and a card hanging off one corner of a mark in the
 * middle of a page points at nothing.
 */
private class InfoPopoverPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2

        // Above the mark instead when there is no room under it, which is what
        // a setting near the foot of a long page always is.
        val below = anchorBounds.bottom + gapPx
        val above = anchorBounds.top - gapPx - popupContentSize.height
        val fitsBelow = below + popupContentSize.height <= windowSize.height - marginPx
        val y = if (fitsBelow || above < marginPx) below else above

        return IntOffset(
            x = clamp(x, popupContentSize.width, windowSize.width),
            y = clamp(y, popupContentSize.height, windowSize.height),
        )
    }

    /**
     * Holds one edge of the card inside the window, keeping [marginPx] where
     * there is room for it. A card wider than the window it is being placed in
     * is pinned to the start rather than centred on its own overflow.
     */
    private fun clamp(offset: Int, size: Int, windowExtent: Int): Int {
        val last = windowExtent - size - marginPx

        return if (last < marginPx) 0 else offset.coerceIn(marginPx, last)
    }
}
