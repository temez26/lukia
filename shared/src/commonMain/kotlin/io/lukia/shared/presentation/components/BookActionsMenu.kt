package io.lukia.shared.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/** Origin of the grow-out animation, matching the corner the menu hangs from. */
private val MenuTransformOrigin = TransformOrigin(1f, 0f)

/**
 * Favourite and remove actions for one book, anchored to the top-end corner of
 * whatever opened it - a card in the library grid, or the cover on the detail
 * screen.
 *
 * A [Popup] rather than a `DropdownMenu`: the menu has to wear the same
 * translucent chrome as the floating action menu and the detail back button,
 * and Material's own elevation and shape are not that.
 *
 * Dismissal is the caller's to complete: the popup is non-focusable so that a
 * press outside it reaches the page underneath, which means every screen that
 * shows this menu also has to place an [ActionMenuScrim] behind it.
 */
@Composable
fun BookActionsMenu(
    expanded: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit
) {
    // Animating through a graphicsLayer rather than wrapping the content in
    // AnimatedVisibility: the latter measures its child at zero size until the
    // enter animation starts, which on Android means the popup window is created
    // 0x0 and never comes back. This keeps the content at full size throughout
    // and only ever touches how it is drawn.
    val progress by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = tween(durationMillis = if (expanded) 180 else 140),
        label = "bookActionsMenu"
    )

    // Stays mounted through the exit animation, then tears the popup down.
    if (!expanded && progress == 0f) return

    val positionProvider = remember { BookActionsMenuPositionProvider() }

    DismissOnBack(enabled = expanded, onDismiss = onDismissRequest)

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(
            // Deliberately not focusable. A focusable popup swallows the press
            // that lands outside it, which leaves the scrim - the way out of
            // this menu - never seeing the tap. It also takes focus while the
            // long press is still in progress on Android, where the tail of
            // that gesture then reads as an outside touch and closes the menu
            // before it has drawn.
            focusable = false
        )
    ) {
        GlassSurface(
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.graphicsLayer {
                alpha = progress
                val scale = 0.9f + 0.1f * progress
                scaleX = scale
                scaleY = scale
                // Grows out of the corner it hangs from.
                transformOrigin = MenuTransformOrigin
            }
        ) {
            // Intrinsic width, not wrap: the rows below fill the menu's width,
            // and inside a Popup "fill" means the whole window unless the
            // column pins itself to its widest row first.
            Column(
                modifier = Modifier
                    .widthIn(min = 208.dp)
                    .width(IntrinsicSize.Max)
            ) {
                val favoriteLabel = if (isFavorite) {
                    "Remove from Favourites"
                } else {
                    "Add to Favourites"
                }

                MenuRow(
                    label = favoriteLabel,
                    tint = MaterialTheme.colorScheme.onSurface,
                    icon = { tint ->
                        HeartGlyph(
                            tint = tint,
                            filled = isFavorite,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    // Closing before acting matches ActionPill: the menu is
                    // never left hanging over the card it just changed.
                    onClick = {
                        onDismissRequest()
                        onToggleFavorite()
                    }
                )

                HorizontalDivider(
                    thickness = Dp.Hairline,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(start = 16.dp)
                )

                MenuRow(
                    label = "Delete from Library",
                    // Destructive rows should read as destructive before
                    // they are read at all.
                    tint = MaterialTheme.colorScheme.error,
                    icon = { tint ->
                        TrashGlyph(tint = tint, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                        onDismissRequest()
                        onDelete()
                    }
                )
            }
        }
    }
}

/**
 * Back on Android, Escape on desktop. The popup is not focusable, so it cannot
 * route either itself.
 *
 * Compose's replacement for this - `NavigationEventHandler` - ships in a further
 * artifact and is shaped around predictive-back progress, which a menu that only
 * ever needs "close" has no use for. Suppressed here rather than on the caller so
 * the deprecation stays visible everywhere else.
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

@Composable
private fun MenuRow(
    label: String,
    tint: Color,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = label }
    ) {
        icon(tint)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tint
        )
    }
}

/**
 * Hangs the menu from the anchor's top-end corner, then clamps it into the
 * window so a card in the last grid column does not push it off screen.
 */
private class BookActionsMenuPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.right - popupContentSize.width
        } else {
            anchorBounds.left
        }

        return IntOffset(
            x = x.coerceIn(0, (windowSize.width - popupContentSize.width).coerceAtLeast(0)),
            y = anchorBounds.top
                .coerceIn(0, (windowSize.height - popupContentSize.height).coerceAtLeast(0))
        )
    }
}
