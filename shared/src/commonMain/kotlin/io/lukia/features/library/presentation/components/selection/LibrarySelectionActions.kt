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
import io.lukia.resources.Res
import io.lukia.resources.selection_delete
import io.lukia.resources.selection_favourite
import io.lukia.resources.selection_mark_unread
import io.lukia.resources.selection_refresh
import io.lukia.resources.selection_unfavourite
import io.lukia.shared.presentation.components.controls.LukiaIconButton
import io.lukia.shared.presentation.components.icons.HeartGlyph
import io.lukia.shared.presentation.components.icons.OpenBookGlyph
import io.lukia.shared.presentation.components.icons.RefreshGlyph
import io.lukia.shared.presentation.components.icons.TrashGlyph
import io.lukia.shared.presentation.components.surface.GlassSurface
import io.lukia.shared.presentation.theme.LocalLukiaDimens
import io.lukia.shared.presentation.theme.Space
import org.jetbrains.compose.resources.stringResource

/**
 * What can be done to the books that have been picked.
 *
 * At the foot of the screen, where a thumb is and where every phone puts a
 * selection mode's actions. The bar at the top says what is selected, this says
 * what can happen to it, and the two are far enough apart that neither is
 * pressed by accident.
 *
 * Greyed until something is selected rather than hidden: a row that appears once
 * the first book is ticked moves the ground under a reader mid-press, and hiding
 * what a mode is for explains it poorly.
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
                label = stringResource(Res.string.selection_refresh),
                onClick = onRefresh,
                enabled = enabled,
            ) { tint, glyph -> RefreshGlyph(tint = tint, modifier = glyph) }

            LukiaIconButton(
                label = stringResource(
                    if (anyFavorite) Res.string.selection_unfavourite else Res.string.selection_favourite,
                ),
                onClick = onToggleFavorite,
                enabled = enabled,
            ) { tint, glyph ->
                HeartGlyph(tint = tint, filled = anyFavorite, modifier = glyph)
            }

            LukiaIconButton(
                label = stringResource(Res.string.selection_mark_unread),
                onClick = onResetProgress,
                enabled = enabled,
            ) { tint, glyph -> OpenBookGlyph(tint = tint, modifier = glyph) }

            LukiaIconButton(
                label = stringResource(Res.string.selection_delete),
                onClick = onDelete,
                enabled = enabled,
                tint = MaterialTheme.colorScheme.error,
            ) { tint, glyph -> TrashGlyph(tint = tint, modifier = glyph) }
        }
    }
}
