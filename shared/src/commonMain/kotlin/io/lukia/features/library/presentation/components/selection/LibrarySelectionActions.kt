package io.lukia.features.library.presentation.components.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.icons.HeartGlyph
import io.lukia.shared.presentation.components.icons.OpenBookGlyph
import io.lukia.shared.presentation.components.icons.RefreshGlyph
import io.lukia.shared.presentation.components.icons.TrashGlyph
import io.lukia.shared.presentation.components.surface.GlassSurface
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space

/**
 * What can be done to the books that have been picked.
 *
 * At the foot of the screen rather than in the bar at the top, which is where a
 * thumb is and where every phone puts the actions of a selection mode. The bar
 * at the top says what is selected; this says what can happen to it, and the
 * two are far enough apart that neither is pressed by accident.
 *
 * Every action is greyed until something is selected, rather than hidden. A row
 * of buttons that appears once the first book is ticked is a row that moves the
 * ground under a reader mid-press, and hiding what a mode is for is a poor way
 * to explain it.
 */
@Composable
internal fun LibrarySelectionActions(
    enabled: Boolean,
    anyFavorite: Boolean,
    onRefresh: () -> Unit,
    onToggleFavorite: () -> Unit,
    onResetProgress: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalLukiaDimens.current

    GlassSurface(
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .widthIn(max = dimens.maxReaderWidth)
            .padding(horizontal = dimens.screenPadding, vertical = Space.md),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth().padding(Space.xs),
        ) {
            LukiaIconButton(
                label = "Refresh details",
                onClick = onRefresh,
                enabled = enabled,
            ) { tint, glyph -> RefreshGlyph(tint = tint, modifier = glyph) }

            LukiaIconButton(
                label = if (anyFavorite) "Remove from favourites" else "Add to favourites",
                onClick = onToggleFavorite,
                enabled = enabled,
            ) { tint, glyph ->
                HeartGlyph(tint = tint, filled = anyFavorite, modifier = glyph)
            }

            LukiaIconButton(
                label = "Mark as unread",
                onClick = onResetProgress,
                enabled = enabled,
            ) { tint, glyph -> OpenBookGlyph(tint = tint, modifier = glyph) }

            LukiaIconButton(
                label = "Delete from library",
                onClick = onDelete,
                enabled = enabled,
                tint = MaterialTheme.colorScheme.error,
            ) { tint, glyph -> TrashGlyph(tint = tint, modifier = glyph) }
        }
    }
}
