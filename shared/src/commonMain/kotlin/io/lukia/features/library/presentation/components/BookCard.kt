package io.lukia.features.library.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
import androidx.compose.runtime.getValue
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
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.presentation.displayTitle
import io.lukia.core.presentation.displayType
import io.lukia.core.presentation.formattedSize
import io.lukia.shared.presentation.components.BookCover
import io.lukia.shared.presentation.components.EllipsisGlyph
import io.lukia.shared.presentation.components.HeartGlyph

/**
 * One book in the library grid.
 *
 * Menu open state is owned by the grid rather than the card, so opening one
 * menu closes any other; the card only reports that a menu was asked for.
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
    modifier: Modifier = Modifier
) {
    val title = document.displayTitle()
    val haptics = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    // Desktop-only in practice: touch platforms never report hover, so the
    // "..." trigger costs nothing on phones, where the long press is the gesture.
    val isHovered by interactionSource.collectIsHoveredAsState()

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
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
                modifier = Modifier.fillMaxWidth()
            )

            FavoriteBadge(
                visible = document.isFavorite,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            )

            MenuTrigger(
                visible = isHovered,
                onClick = onMenuRequest,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
            )

            // Anchored here so the popup hangs off the cover's top-end corner
            // rather than the caption below it.
            BookCardMenu(
                expanded = menuExpanded,
                isFavorite = document.isFavorite,
                onToggleFavorite = onToggleFavorite,
                onDelete = onDelete,
                onDismissRequest = onMenuDismiss
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
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )

        if (!document.exists) {
            UnavailableBadge(modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/**
 * Decorative: the favourite state is already announced by the menu row that
 * toggles it, so the badge stays out of the accessibility tree.
 */
@Composable
private fun FavoriteBadge(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        // The pop is the feedback for a toggle made from a menu that has
        // already closed over the card.
        enter = fadeIn(tween(durationMillis = 160)) +
            scaleIn(
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                initialScale = 0.5f
            ),
        exit = fadeOut(tween(durationMillis = 120)) +
            scaleOut(tween(durationMillis = 120), targetScale = 0.5f),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            HeartGlyph(
                tint = MaterialTheme.colorScheme.primary,
                filled = true,
                modifier = Modifier.size(12.dp)
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
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis = 140)) +
            scaleIn(tween(durationMillis = 140), initialScale = 0.8f),
        exit = fadeOut(tween(durationMillis = 120)) +
            scaleOut(tween(durationMillis = 120), targetScale = 0.8f),
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
