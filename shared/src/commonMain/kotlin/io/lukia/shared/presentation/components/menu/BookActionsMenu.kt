package io.lukia.shared.presentation.components.menu

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import io.lukia.shared.presentation.components.icons.CheckGlyph
import io.lukia.shared.presentation.components.icons.HeartGlyph
import io.lukia.shared.presentation.components.icons.InfoGlyph
import io.lukia.shared.presentation.components.icons.RefreshGlyph
import io.lukia.shared.presentation.components.icons.TrashGlyph

/**
 * Actions for one book, hanging under whatever opened it - a card in the library
 * grid, or the cover on the detail screen.
 *
 * Under rather than over: a menu covering the book it acts on hides the very
 * thing the reader is choosing about, and on a phone a card is narrower than this
 * menu, so an overlaid menu swallows it whole. This is also where the library's
 * own menu sits under its button, which is what makes the two read as one app.
 *
 * [onShowDetails] and [onSelectBooks] are left out where they have nowhere to
 * go: the details when they are already on screen, and selecting when the menu
 * was opened from somewhere that has no shelf to select from.
 *
 * Every row closes the menu before acting, so it is never left hanging over the
 * card it just changed.
 */
@Composable
fun BookActionsMenu(
    expanded: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
    onShowDetails: (() -> Unit)? = null,
    onSelectBooks: (() -> Unit)? = null,
) {
    AnchoredMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
    ) {
        if (onShowDetails != null) {
            MenuRow(
                label = "Book Details",
                icon = { tint, glyph -> InfoGlyph(tint = tint, modifier = glyph) },
                onClick = {
                    onDismissRequest()
                    onShowDetails()
                },
            )

            MenuSeparator()
        }

        MenuRow(
            label = if (isFavorite) "Remove from Favourites" else "Add to Favourites",
            icon = { tint, glyph ->
                HeartGlyph(tint = tint, filled = isFavorite, modifier = glyph)
            },
            onClick = {
                onDismissRequest()
                onToggleFavorite()
            },
        )

        MenuSeparator()

        // Above the actions rather than below them: this one changes what the
        // shelf is for rather than doing anything to this book, and a row that
        // sits among Refresh and Delete reads as a third thing done to the book
        // it was opened from.
        if (onSelectBooks != null) {
            MenuRow(
                label = "Select Books",
                icon = { tint, glyph -> CheckGlyph(tint = tint, modifier = glyph) },
                onClick = {
                    onDismissRequest()
                    onSelectBooks()
                },
            )

            MenuSeparator()
        }

        MenuRow(
            label = "Refresh Details",
            icon = { tint, glyph -> RefreshGlyph(tint = tint, modifier = glyph) },
            onClick = {
                onDismissRequest()
                onRefresh()
            },
        )

        MenuSeparator()

        MenuRow(
            label = "Delete from Library",
            tint = MaterialTheme.colorScheme.error,
            icon = { tint, glyph -> TrashGlyph(tint = tint, modifier = glyph) },
            onClick = {
                onDismissRequest()
                onDelete()
            },
        )
    }
}
