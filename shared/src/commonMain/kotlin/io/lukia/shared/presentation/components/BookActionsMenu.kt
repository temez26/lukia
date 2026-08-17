package io.lukia.shared.presentation.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Actions for one book, hanging under whatever opened it - a card in the library
 * grid, or the cover on the detail screen.
 *
 * Under rather than over: a menu covering the book it acts on hides the very
 * thing the reader is choosing about, and on a phone a card is narrower than this
 * menu, so an overlaid menu swallows it whole. This is also where the library's
 * own menu sits under its button, which is what makes the two read as one app.
 *
 * [onShowDetails] is left out where the book's details are already on screen,
 * which is why it is the one optional row.
 *
 * Every row closes the menu before acting, so it is never left hanging over the
 * card it just changed.
 */
@Composable
fun BookActionsMenu(
    expanded: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onDismissRequest: () -> Unit,
    onShowDetails: (() -> Unit)? = null
) {
    AnchoredMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        if (onShowDetails != null) {
            MenuRow(
                label = "Book Details",
                icon = { tint, glyph -> InfoGlyph(tint = tint, modifier = glyph) },
                onClick = {
                    onDismissRequest()
                    onShowDetails()
                }
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
            }
        )

        MenuSeparator()

        MenuRow(
            label = "Delete from Library",
            tint = MaterialTheme.colorScheme.error,
            icon = { tint, glyph -> TrashGlyph(tint = tint, modifier = glyph) },
            onClick = {
                onDismissRequest()
                onDelete()
            }
        )
    }
}
