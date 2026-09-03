package io.lukia.shared.presentation.components.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import io.lukia.shared.presentation.components.controls.focusRing
import io.lukia.shared.presentation.components.controls.hoverHighlight
import io.lukia.shared.presentation.components.icons.CheckGlyph
import io.lukia.shared.presentation.components.surface.GlassSurface
import io.lukia.shared.presentation.components.surface.GlassTone
import io.lukia.shared.presentation.components.surface.LukiaDivider
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.Space

/** Gap between the menu and the control it hangs from. */
private val AnchorGap = Space.sm

/** Inset from a menu's edge to its rows, and what its separators are inset by. */
private val MenuInset = Space.lg

/**
 * A menu hanging under whatever composable it is placed inside, end-aligned with
 * it.
 *
 * Always under, never over. A menu drawn on top of its anchor hides the thing the
 * reader is choosing about, and every anchor in this app - a nav bar button, a
 * book cover - is something worth keeping in sight while its own menu is open.
 *
 * A [Popup] rather than a `DropdownMenu`: these wear the app's translucent chrome
 * and its corner radii, and Material's own elevation and shape are not that.
 *
 * Dismissal is the caller's to complete. The popup is deliberately non-focusable
 * so a press outside it reaches the page underneath, which means every screen
 * showing one of these also places an [ActionMenuScrim] behind it. A focusable
 * popup would swallow that press - leaving the scrim, the way out of the menu,
 * never seeing the tap - and on Android would take focus while a long press was
 * still in progress, so the tail of that gesture read as an outside touch and
 * closed the menu before it had drawn.
 */
@Composable
fun AnchoredMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    minWidth: Dp = 196.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val progress = rememberPopoverProgress(expanded, label = "anchoredMenu")

    // Stays mounted through the exit animation, then tears the popup down.
    if (!expanded && progress == 0f) return

    DismissOnBack(enabled = expanded, onDismiss = onDismissRequest)

    val gapPx = with(LocalDensity.current) { AnchorGap.roundToPx() }
    val positionProvider = remember(gapPx) { AnchoredMenuPositionProvider(gapPx) }

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = false),
    ) {
        GlassSurface(
            shape = MaterialTheme.shapes.medium,
            tone = GlassTone.Panel,
            modifier = modifier.popoverTransition(
                progress = progress,
                transformOrigin = TransformOrigin(1f, 0f),
            ),
        ) {
            // Intrinsic width, not wrap: the rows below fill the menu's width,
            // and inside a Popup "fill" means the whole window unless the column
            // pins itself to its widest row first.
            Column(
                modifier = Modifier
                    .widthIn(min = minWidth)
                    .width(IntrinsicSize.Max),
                content = content,
            )
        }
    }
}

/**
 * One action in an [AnchoredMenu].
 *
 * [tint] carries the row's meaning as well as its colour - a destructive row
 * should read as destructive before it is read at all - so the glyph and the
 * label are drawn in it together.
 *
 * [icon] is handed both the tint and an already-sized modifier, so the glyphs
 * down a menu are the same size without every caller having to say so.
 *
 * [selected] is for a menu that is a list of choices rather than a list of
 * actions - which format to show, which order to sort in. The tick sits at the
 * end of the row, in the accent colour, which is where iOS puts the mark on a
 * chosen menu item, and the row carries the selected flag so the mark is
 * announced rather than only drawn.
 *
 * The role stays [Role.Button] either way. A row that is a choice is still a
 * button that is currently the chosen one - which is how the platforms
 * themselves announce a ticked menu item - and a menu cannot tell an unticked
 * choice from a plain action to say anything more precise.
 */
@Composable
fun MenuRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    selected: Boolean = false,
    icon: (@Composable (Color, Modifier) -> Unit)? = null,
) {
    val metrics = LocalLukiaMetrics.current
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            // Square, because the row runs the full width of the menu and a
            // rounded highlight inside a square menu leaves gaps at the corners.
            .hoverHighlight(interactionSource, RectangleShape)
            .focusRing(interactionSource, RectangleShape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .heightIn(min = metrics.rowHeight)
            .padding(horizontal = MenuInset, vertical = Space.sm)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
    ) {
        if (icon != null) {
            icon(tint, Modifier.size(metrics.iconSmall))
            Spacer(modifier = Modifier.width(Space.lg))
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Yields to the tick beside it rather than pushing it off the row:
            // the menu is as wide as its widest label, and a label longer than
            // the menu can hold has to give the ground back.
            modifier = Modifier.weight(1f, fill = false),
        )

        if (selected) {
            // The column is pinned to its widest row, so the weight pushes the
            // tick to the end of the menu rather than to the end of the label.
            Spacer(modifier = Modifier.weight(1f).width(Space.lg))

            CheckGlyph(
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(metrics.iconSmall),
            )
        }
    }
}

/** Hairline between menu rows, inset from the left in the way iOS insets its own. */
@Composable
fun MenuSeparator() {
    LukiaDivider(inset = MenuInset)
}

/**
 * Places the menu under its anchor, then clamps it into the window so that a card
 * in the last grid column - or one in the bottom row, whose menu would otherwise
 * open below the screen - does not push its menu out of sight.
 */
private class AnchoredMenuPositionProvider(
    private val gapPx: Int,
) : PopupPositionProvider {

    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - popupContentSize.width
        } else {
            anchorBounds.left
        }

        return IntOffset(
            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y = (anchorBounds.bottom + gapPx)
                .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0)),
        )
    }
}
