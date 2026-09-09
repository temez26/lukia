package io.lukia.features.library.presentation.sessions

import androidx.compose.runtime.Stable
import io.lukia.features.library.presentation.model.LibraryBook

/**
 * Everything the home screen can do to one of the books it is showing.
 *
 * Gathered rather than passed one lambda at a time, as [LibraryShelfUi] gathers
 * the shelf's: these six travel together down three composables that only hand
 * them on, and repeating six parameters at every step makes a signature about
 * plumbing rather than about the screen.
 *
 * [onSelectBooks] takes no book, being the odd one out: it turns the shelf into
 * something to pick from rather than acting on the book the menu was opened
 * from - see [LibraryShelfUi.selectingOn].
 */
@Stable
class HomeBookActions(
    val onOpen: (LibraryBook) -> Unit = {},
    val onShowDetails: (LibraryBook) -> Unit = {},
    val onToggleFavorite: (LibraryBook) -> Unit = {},
    val onRefresh: (LibraryBook) -> Unit = {},
    val onDelete: (LibraryBook) -> Unit = {},
    val onSelectBooks: () -> Unit = {},
) {
    companion object {
        val None = HomeBookActions()
    }
}
