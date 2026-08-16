package io.lukia.features.library.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.presentation.displayTitle
import io.lukia.core.presentation.displayType
import io.lukia.core.presentation.formattedSize
import io.lukia.shared.presentation.components.BookActionsMenu
import io.lukia.shared.presentation.components.BookCover
import io.lukia.shared.presentation.components.EllipsisGlyph
import io.lukia.shared.presentation.components.FavoriteBadge
import io.lukia.shared.presentation.components.LaunchReveal
import io.lukia.shared.presentation.components.hoverLift
import io.lukia.shared.presentation.components.launchReveal
import io.lukia.shared.presentation.components.rememberHoverFraction
import io.lukia.shared.presentation.components.scaleOnPress
import io.lukia.shared.presentation.theme.LukiaMotion

/**
 * One book in the library grid.
 *
 * Menu open state is owned by the grid rather than the card, so opening one menu
 * closes any other; the card only reports that a menu was asked for.
 *
 * The card carries its feedback as motion rather than as a ripple: it shrinks
 * under a press and, on desktop, lifts and casts a deeper shadow under the
 * pointer. A ripple washing across a book cover reads as a defect in the artwork.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun BookCard(
    document: DocumentInfo,
    menuExpanded: Boolean,
    onClick: () -> Unit,
    onMenuRequest: () -> Unit,
    onMenuDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
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

    Column(
        modifier = modifier
            .launchReveal(reveal, index = revealIndex)
            .hoverLift(fraction = hover, lift = 5.dp, hoveredScale = 1.035f)
            .scaleOnPress(interactionSource, pressedScale = 0.96f)
            .clip(RoundedCornerShape(16.dp))
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
            .padding(4.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            BookCover(
                coverImage = document.coverImage,
                title = title,
                // Deepens as the card lifts, so the two read as one movement
                // rather than as a card that floats without leaving the page.
                elevation = lerp(6.dp, 18.dp, hover),
                modifier = Modifier.fillMaxWidth()
            )

            FavoriteBadge(
                visible = document.isFavorite,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            )

            MenuTrigger(
                // Any hover at all, so the trigger is on its way in from the
                // moment the pointer arrives rather than halfway through the
                // card's own lift.
                visible = hover > 0f,
                onClick = onMenuRequest,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )

            // Anchored here so the popup hangs off the cover's top-end corner
            // rather than the caption below it.
            BookActionsMenu(
                expanded = menuExpanded,
                isFavorite = document.isFavorite,
                onToggleFavorite = onToggleFavorite,
                onDelete = onDelete,
                onDismissRequest = onMenuDismiss,
                onShowDetails = onShowDetails
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            // Reserving two lines keeps the metadata row aligned across the grid
            // whether a title wraps or not.
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 10.dp)
        )

        Text(
            text = "${document.displayType()} · ${document.formattedSize()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp)
        )

        if (!document.exists) {
            UnavailableBadge(modifier = Modifier.padding(top = 6.dp))
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
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(LukiaMotion.quick()) +
            scaleIn(LukiaMotion.quick(), initialScale = 0.8f),
        exit = fadeOut(LukiaMotion.exit()) +
            scaleOut(LukiaMotion.exit(), targetScale = 0.8f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(onClickLabel = "Book actions", onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            EllipsisGlyph(
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun UnavailableBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = "Unavailable",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
}
