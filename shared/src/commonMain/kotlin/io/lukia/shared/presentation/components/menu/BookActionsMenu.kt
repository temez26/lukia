package io.lukia.shared.presentation.components.menu

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.lukia.resources.Res
import io.lukia.resources.menu_book_details
import io.lukia.resources.menu_delete_from_library
import io.lukia.resources.menu_favourite
import io.lukia.resources.menu_refresh_details
import io.lukia.resources.menu_remove_from_server
import io.lukia.resources.menu_select_books
import io.lukia.resources.menu_unfavourite
import io.lukia.shared.presentation.components.icons.CheckGlyph
import io.lukia.shared.presentation.components.icons.HeartGlyph
import io.lukia.shared.presentation.components.icons.InfoGlyph
import io.lukia.shared.presentation.components.icons.RefreshGlyph
import io.lukia.shared.presentation.components.icons.TrashGlyph
import org.jetbrains.compose.resources.stringResource

/**
 * Actions for one book, hanging under whatever opened it - a card in the library
 * grid, or the cover on the detail screen.
 *
 * Under rather than over: a menu covering the book it acts on hides the thing
 * the reader is choosing about, and on a phone a card is narrower than the menu,
 * so an overlaid one swallows it whole. The library's own menu sits under its
 * button for the same reason, which is what makes the two read as one app.
 *
 * [onShowDetails] and [onSelectBooks] are left out where they have nowhere to
 * go: the details when they are already on screen, and selecting when the menu
 * was opened from somewhere that has no shelf to select from. [onRemoveFromServer]
 * is there only for the server's admin, and only on a book the server has.
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
    modifier: Modifier = Modifier,
    onShowDetails: (() -> Unit)? = null,
    onSelectBooks: (() -> Unit)? = null,
    onRemoveFromServer: (() -> Unit)? = null,
) {
    AnchoredMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        if (onShowDetails != null) {
            MenuRow(
                label = stringResource(Res.string.menu_book_details),
                icon = { tint, glyph -> InfoGlyph(tint = tint, modifier = glyph) },
                onClick = {
                    onDismissRequest()
                    onShowDetails()
                },
            )

            MenuSeparator()
        }

        MenuRow(
            label = stringResource(if (isFavorite) Res.string.menu_unfavourite else Res.string.menu_favourite),
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
                label = stringResource(Res.string.menu_select_books),
                icon = { tint, glyph -> CheckGlyph(tint = tint, modifier = glyph) },
                onClick = {
                    onDismissRequest()
                    onSelectBooks()
                },
            )

            MenuSeparator()
        }

        MenuRow(
            label = stringResource(Res.string.menu_refresh_details),
            icon = { tint, glyph -> RefreshGlyph(tint = tint, modifier = glyph) },
            onClick = {
                onDismissRequest()
                onRefresh()
            },
        )

        MenuSeparator()

        RemovingRow(stringResource(Res.string.menu_delete_from_library), onDismissRequest, onDelete)

        // Last and apart from Delete: Delete is about this device, this is
        // about everyone's library.
        if (onRemoveFromServer != null) {
            MenuSeparator()

            RemovingRow(stringResource(Res.string.menu_remove_from_server), onDismissRequest, onRemoveFromServer)
        }
    }
}

/** A row that takes the book away from somewhere: red, with a bin. */
@Composable
private fun RemovingRow(label: String, onDismissRequest: () -> Unit, onClick: () -> Unit) {
    MenuRow(
        label = label,
        tint = MaterialTheme.colorScheme.error,
        icon = { tint, glyph -> TrashGlyph(tint = tint, modifier = glyph) },
        onClick = {
            onDismissRequest()
            onClick()
        },
    )
}
