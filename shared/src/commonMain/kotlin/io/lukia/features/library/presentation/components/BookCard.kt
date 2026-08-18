package io.lukia.features.library.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.zIndex
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.presentation.displayTitle
import io.lukia.core.presentation.displayType
import io.lukia.core.presentation.formattedSize
import io.lukia.shared.presentation.components.BookActionsMenu
import io.lukia.shared.presentation.components.BookCover
import io.lukia.shared.presentation.components.EllipsisGlyph
import io.lukia.shared.presentation.components.FavoriteBadge
import io.lukia.shared.presentation.components.IconButtonVariant
import io.lukia.shared.presentation.components.LaunchReveal
import io.lukia.shared.presentation.components.LukiaIconButton
import io.lukia.shared.presentation.components.ReadingProgressBar
import io.lukia.shared.presentation.components.StatusBadge
import io.lukia.shared.presentation.components.focusRing
import io.lukia.shared.presentation.components.hoverLift
import io.lukia.shared.presentation.components.launchReveal
import io.lukia.shared.presentation.components.rememberHoverFraction
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.LukiaElevation
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.Space

/**
 * Thinner than the bar on the detail screen. A cover in the grid is a third of
 * the size, and a bar scaled for the larger one reads as a stripe painted across
 * the artwork rather than as a measure of it.
 */
private val CardProgressBarHeight: Dp = 3.dp

/**
 * One book in the library grid.
 *
 * Menu open state is owned by the grid rather than the card, so opening one menu
 * closes any other; the card only reports that a menu was asked for.
 *
 * The card carries its feedback as motion rather than as a ripple: it shrinks
 * under a press and, on desktop, lifts and casts a deeper shadow under the
 * pointer. A ripple washing across a book cover reads as a defect in the artwork.
 *
 * Nothing here clips. The cover casts its shadow deliberately outside its own
 * bounds, and a clip anywhere up the chain crops that shadow to a hard edge -
 * which is what used to make a hovered card look as though its shadow had been
 * cut off square, more visibly the further it lifted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookCard(
    document: DocumentInfo,
    /** How far through, 0 to 1. Null for a book that has never been opened. */
    progress: Float?,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onMenuRequest: () -> Unit,
    onMenuDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onShowDetails: () -> Unit,
    reveal: LaunchReveal,
    revealIndex: Int,
    modifier: Modifier = Modifier
) {
    val title = document.displayTitle()
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val hover = rememberHoverFraction(interactionSource)
    val coverShape = MaterialTheme.shapes.medium

    Column(
        modifier = modifier
            // Raised above its neighbours while it is lifted, so the deepened
            // shadow is not painted over by the next cell in the row.
            .zIndex(if (hover > 0f) 1f else 0f)
            .launchReveal(reveal, index = revealIndex)
            // One layer for both gestures. Two stacked `graphicsLayer`s meant the
            // press scaled a layer that was already scaling for hover, and each
            // added a compositing pass the grid paid for on every frame.
            .hoverLift(
                interactionSource = interactionSource,
                fraction = hover,
                lift = 4.dp,
                hoveredScale = 1.03f,
                pressedScale = 0.97f
            )
            .pointerHoverIcon(PointerIcon.Hand)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClickLabel = "Book actions",
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMenuRequest()
                }
            )
            // Leaves the cover's shadow somewhere to fall inside the cell rather
            // than straight onto the neighbouring one.
            .padding(Space.xs)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BookCover(
                coverImage = document.coverImage,
                title = title,
                shape = coverShape,
                // Deepens as the card lifts, so the two read as one movement
                // rather than as a card that floats without leaving the page.
                elevation = lerp(LukiaElevation.card, LukiaElevation.cardHovered, hover),
                modifier = Modifier
                    .fillMaxWidth()
                    // The card is what the keyboard focuses, and the cover is the
                    // part of it worth ringing.
                    .focusRing(interactionSource, coverShape)
            )

            // Clipped to the cover's own shape so the bar stops where the
            // rounded corners do, and drawn only for a book somebody has
            // started - an empty bar on every unread cover would turn a shelf
            // into a progress report.
            if (progress != null) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(coverShape)
                ) {
                    ReadingProgressBar(
                        fraction = progress,
                        height = CardProgressBarHeight,
                        modifier = Modifier.align(Alignment.BottomStart)
                    )
                }
            }

            FavoriteBadge(
                visible = document.isFavorite,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(Space.sm)
            )

            MenuTrigger(
                // Any hover at all, so the trigger is on its way in from the
                // moment the pointer arrives rather than halfway through the
                // card's own lift.
                visible = hover > 0f,
                onClick = onMenuRequest,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Space.xs)
            )

            // Anchored here so the popup hangs off the cover's top-end corner
            // rather than the caption below it.
            BookActionsMenu(
                expanded = menuExpanded,
                isFavorite = document.isFavorite,
                onToggleFavorite = onToggleFavorite,
                onRefresh = onRefresh,
                onDelete = onDelete,
                onDismissRequest = onMenuDismiss,
                onShowDetails = onShowDetails
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            // Reserving two lines keeps the metadata row aligned across the grid
            // whether a title wraps or not.
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.md)
        )

        Text(
            text = "${document.displayType()} · ${document.formattedSize()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Space.xxs)
        )

        if (!document.exists) {
            StatusBadge(
                text = "Unavailable",
                modifier = Modifier.padding(top = Space.sm)
            )
        }
    }
}

/** Gives desktop users something visible to aim at for the long-press gesture. */
@Composable
private fun MenuTrigger(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = LocalLukiaMetrics.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(LukiaMotion.quick()) +
            scaleIn(LukiaMotion.quick(), initialScale = 0.8f),
        exit = fadeOut(LukiaMotion.exit()) +
            scaleOut(LukiaMotion.exit(), targetScale = 0.8f),
        modifier = modifier
    ) {
        LukiaIconButton(
            label = "Book actions",
            onClick = onClick,
            variant = IconButtonVariant.Filled,
            tint = MaterialTheme.colorScheme.onSurface,
            size = metrics.iconButtonSmall,
            glyphSize = metrics.iconSmall
        ) { tint, glyph ->
            EllipsisGlyph(tint = tint, modifier = glyph)
        }
    }
}
