package io.lukia.features.library.presentation.sessions

import androidx.compose.runtime.Stable
import io.lukia.features.library.domain.LibraryLayout
import io.lukia.features.library.presentation.model.BulkAction
import io.lukia.features.library.presentation.model.LibrarySelection
import io.lukia.features.library.presentation.model.LibraryUpkeep
import io.lukia.features.library.presentation.model.RemoveFrom

/**
 * How the shelf is being worked with: how it is laid out, what is picked off it,
 * and everything that can be done to what is picked.
 *
 * A dozen parameters that always arrive together and are only ever read
 * together, gathered so that the screen's own signature stays about the screen -
 * the same bargain `ReaderTranslationUi` makes for the reader's controls.
 */
@Stable
class LibraryShelfUi(
    val selection: LibrarySelection = LibrarySelection.None,
    /** What a bulk action is doing right now, or null when none is running. */
    val running: BulkAction? = null,
    val onStart: () -> Unit = {},
    val onStop: () -> Unit = {},
    val onToggle: (location: String) -> Unit = {},
    val onToggleAll: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onFavorite: () -> Unit = {},
    val onResetProgress: () -> Unit = {},
    /** Removes the selection from where the reader chose - see [RemoveFrom]. */
    val onDelete: (RemoveFrom) -> Unit = {},
    /** Whether the server's shared library is the reader's to remove from - true for its admin. */
    val canRemoveFromServer: Boolean = false,
    /** Whether there is a server to add the selected books to - true while signed in. */
    val canUpload: Boolean = false,
    val onUpload: () -> Unit = {},
    /** What is worth tidying on the shelf, for the menu that offers to. */
    val upkeep: LibraryUpkeep = LibraryUpkeep.None,
    /** Ticks the books whose files have gone. */
    val onSelectMissing: () -> Unit = {},
    /** Ticks every copy but the first of each book that is on the shelf twice. */
    val onSelectDuplicates: () -> Unit = {},
    /** Covers or rows, which is remembered between sessions. */
    val layout: LibraryLayout = LibraryLayout.Grid,
    val onLayoutChange: (LibraryLayout) -> Unit = {},
    /** Catches up with the sync server and shows how that went - or where to sign in to one. */
    val onSyncWithServer: () -> Unit = {},
) {
    val active: Boolean get() = selection.active

    /**
     * This shelf, with everything that starts a selection taking the reader to
     * [shelf] first.
     *
     * For the home screen, which offers the library's own menu but has nowhere
     * to put the selection bar and the row of actions that come with it. Rather
     * than a second, shorter menu to keep in step with the first, the rows that
     * need the shelf go and find it.
     */
    fun selectingOn(shelf: () -> Unit): LibraryShelfUi = LibraryShelfUi(
        selection = selection,
        running = running,
        onStart = {
            shelf()
            onStart()
        },
        onStop = onStop,
        onToggle = onToggle,
        onToggleAll = onToggleAll,
        onRefresh = onRefresh,
        onFavorite = onFavorite,
        onResetProgress = onResetProgress,
        onDelete = onDelete,
        canRemoveFromServer = canRemoveFromServer,
        canUpload = canUpload,
        onUpload = onUpload,
        upkeep = upkeep,
        onSelectMissing = {
            shelf()
            onSelectMissing()
        },
        onSelectDuplicates = {
            shelf()
            onSelectDuplicates()
        },
        layout = layout,
        onLayoutChange = onLayoutChange,
        onSyncWithServer = onSyncWithServer,
    )

    companion object {
        val None = LibraryShelfUi()
    }
}
