package io.lukia.features.library.presentation.components.book

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.features.library.presentation.components.selection.CardSelection
import io.lukia.features.library.presentation.components.selection.SelectionMark
import io.lukia.resources.Res
import io.lukia.resources.book_actions
import io.lukia.shared.presentation.components.controls.IconButtonVariant
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.cover.FavoriteBadge
import io.lukia.shared.presentation.components.icons.EllipsisGlyph
import io.lukia.shared.presentation.components.menu.BookActionsMenu
import io.lukia.shared.presentation.theme.LocalLukiaMetrics
import io.lukia.shared.presentation.theme.LukiaMotion
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/** Big enough to be a target on a phone, small enough not to sit on the artwork. */
private val SelectionMarkSize: Dp = 24.dp

/**
 * What is drawn over a cover: what the book already is, and what can be done to
 * it from here.
 *
 * Apart from the card itself because these are its chrome rather than its
 * shape - the card is a cover, a caption and how it answers a press, and this is
 * everything the shelf has hung on it.
 */
@Composable
internal fun BoxScope.CoverOverlays(
    document: DocumentInfo,
    hover: Float,
    menuExpanded: Boolean,
    selection: CardSelection?,
    onMenuRequest: () -> Unit,
    onMenuDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onShowDetails: () -> Unit,
    onSelectBooks: () -> Unit,
    onRemoveFromServer: (() -> Unit)? = null,
) {
    FavoriteBadge(
        visible = document.isFavorite,
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(Space.sm),
    )

    if (selection == null) {
        MenuTrigger(
            // Any hover at all, so the trigger is on its way in from the
            // moment the pointer arrives rather than halfway through the
            // card's own lift.
            visible = hover > 0f,
            onClick = onMenuRequest,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Space.xs),
        )
    } else {
        // Where the menu trigger was: the corner a reader already looks
        // to for what this card can do.
        SelectionMark(
            selected = selection.selected,
            size = SelectionMarkSize,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(Space.sm),
        )
    }

    // Anchored here so the popup hangs off the cover's top-end corner
    // rather than the caption below it.
    if (selection == null) {
        BookActionsMenu(
            expanded = menuExpanded,
            isFavorite = document.isFavorite,
            onToggleFavorite = onToggleFavorite,
            onRefresh = onRefresh,
            onDelete = onDelete,
            onDismissRequest = onMenuDismiss,
            onShowDetails = onShowDetails,
            onSelectBooks = onSelectBooks,
            onRemoveFromServer = onRemoveFromServer,
        )
    }
}

/** Gives desktop users something visible to aim at for the long-press gesture. */
@Composable
private fun MenuTrigger(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metrics = LocalLukiaMetrics.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(LukiaMotion.quick()) +
            scaleIn(LukiaMotion.quick(), initialScale = 0.8f),
        exit = fadeOut(LukiaMotion.exit()) +
            scaleOut(LukiaMotion.exit(), targetScale = 0.8f),
        modifier = modifier,
    ) {
        LukiaIconButton(
            label = stringResource(Res.string.book_actions),
            onClick = onClick,
            variant = IconButtonVariant.Filled,
            tint = MaterialTheme.colorScheme.onSurface,
            size = metrics.iconButtonSmall,
            glyphSize = metrics.iconSmall,
        ) { tint, glyph ->
            EllipsisGlyph(tint = tint, modifier = glyph)
        }
    }
}
