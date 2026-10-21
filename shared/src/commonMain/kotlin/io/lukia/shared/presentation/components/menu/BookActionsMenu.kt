package io.lukia.shared.presentation.components.menu

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.lukia.resources.Res
import io.lukia.resources.menu_book_details
import io.lukia.resources.menu_favourite
import io.lukia.resources.menu_mark_unread
import io.lukia.resources.menu_refresh_details
import io.lukia.resources.menu_remove_from_device
import io.lukia.resources.menu_remove_from_server
import io.lukia.resources.menu_select_books
import io.lukia.resources.menu_unfavourite
import io.lukia.shared.presentation.components.icons.CheckGlyph
import io.lukia.shared.presentation.components.icons.HeartGlyph
import io.lukia.shared.presentation.components.icons.InfoGlyph
import io.lukia.shared.presentation.components.icons.RefreshGlyph
import io.lukia.shared.presentation.components.icons.TrashGlyph
import io.lukia.shared.presentation.components.icons.UnreadBookGlyph
import io.lukia.shared.presentation.theme.OnPageColors
import io.lukia.shared.presentation.theme.isWideWindow
import org.jetbrains.compose.resources.stringResource

/**
 * Actions for one book, hanging under whatever opened it - a card in the library
 * grid, or the cover on the detail screen. On a phone they come up from the
 * bottom of the screen instead, as [BookActionsSheet], with [header] naming the
 * book at the top; a menu hung off a cover there is a reach across the screen.
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
    /** Puts the book back to unread; null, and no row, for a book not yet started. */
    onMarkUnread: (() -> Unit)? = null,
    /** False for a book only on the server, which has nothing on this device to remove. */
    canRemoveFromDevice: Boolean = true,
    /** The book, for the top of the phone's sheet; null where the book is already on screen. */
    header: BookMenuHeader? = null,
) {
    val choices = BookMenuChoices(
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
        onRefresh = onRefresh,
        onDelete = onDelete,
        onShowDetails = onShowDetails,
        onSelectBooks = onSelectBooks,
        onRemoveFromServer = onRemoveFromServer,
        onMarkUnread = onMarkUnread,
        canRemoveFromDevice = canRemoveFromDevice,
    )

    OnPageColors {
        if (isWideWindow) {
            AnchoredBookMenu(expanded, choices, onDismissRequest, modifier)
        } else {
            BookActionsSheet(expanded, choices, onDismissRequest, header)
        }
    }
}

/** The menu hung under the cover it was opened from, for anything wider than a phone. */
@Composable
private fun AnchoredBookMenu(
    expanded: Boolean,
    choices: BookMenuChoices,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnchoredMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        OptionalRow(
            label = stringResource(Res.string.menu_book_details),
            icon = { tint, glyph -> InfoGlyph(tint = tint, modifier = glyph) },
            action = choices.onShowDetails,
            onDismissRequest = onDismissRequest,
        )

        MenuRow(
            label = stringResource(if (choices.isFavorite) Res.string.menu_unfavourite else Res.string.menu_favourite),
            icon = { tint, glyph ->
                HeartGlyph(tint = tint, filled = choices.isFavorite, modifier = glyph)
            },
            onClick = {
                onDismissRequest()
                choices.onToggleFavorite()
            },
        )

        MenuSeparator()

        // Beside favouriting: both are things decided about this one book,
        // and the selection bar offers the same for many at once.
        OptionalRow(
            label = stringResource(Res.string.menu_mark_unread),
            icon = { tint, glyph -> UnreadBookGlyph(tint = tint, modifier = glyph) },
            action = choices.onMarkUnread,
            onDismissRequest = onDismissRequest,
        )

        // Above the actions rather than below them: this one changes what the
        // shelf is for rather than doing anything to this book, and a row that
        // sits among Refresh and Delete reads as a third thing done to the book
        // it was opened from.
        OptionalRow(
            label = stringResource(Res.string.menu_select_books),
            icon = { tint, glyph -> CheckGlyph(tint = tint, modifier = glyph) },
            action = choices.onSelectBooks,
            onDismissRequest = onDismissRequest,
        )

        MenuRow(
            label = stringResource(Res.string.menu_refresh_details),
            icon = { tint, glyph -> RefreshGlyph(tint = tint, modifier = glyph) },
            onClick = {
                onDismissRequest()
                choices.onRefresh()
            },
        )

        if (choices.canRemoveFromDevice) {
            MenuSeparator()

            RemovingRow(stringResource(Res.string.menu_remove_from_device), onDismissRequest, choices.onDelete)
        }

        // Last and apart from the device's row: that one is about this device,
        // this is about everyone's library.
        choices.onRemoveFromServer?.let { remove ->
            MenuSeparator()

            RemovingRow(stringResource(Res.string.menu_remove_from_server), onDismissRequest, remove)
        }
    }
}

/** A row and the hairline under it, where the caller offers [action]; nothing where it does not. */
@Composable
private fun OptionalRow(
    label: String,
    icon: @Composable (Color, Modifier) -> Unit,
    action: (() -> Unit)?,
    onDismissRequest: () -> Unit,
) {
    if (action == null) return

    MenuRow(
        label = label,
        icon = icon,
        onClick = {
            onDismissRequest()
            action()
        },
    )

    MenuSeparator()
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
