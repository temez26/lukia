package io.lukia.features.library.presentation

import androidx.compose.runtime.Stable

/**
 * Everything the home screen can do to one of the books it is showing.
 *
 * Gathered rather than passed one lambda at a time, for the reason
 * [LibraryShelfUi] gathers the shelf's: these six always travel together, down
 * three composables that only hand them on, and six parameters repeated at every
 * step is a signature about plumbing rather than about the screen.
 *
 * [onSelectBooks] takes no book because it is the odd one out - it turns the
 * shelf into something to pick from rather than doing anything to the book the
 * menu was opened from, and on this screen that means going to the shelf first;
 * see [LibraryShelfUi.selectingOn].
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
