package io.lukia.features.library.presentation

import androidx.compose.runtime.Stable

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
    val onDelete: () -> Unit = {},
    /** What is worth tidying on the shelf, for the menu that offers to. */
    val upkeep: LibraryUpkeep = LibraryUpkeep.None,
    /** Ticks the books whose files have gone. */
    val onSelectMissing: () -> Unit = {},
    /** Ticks every copy but the first of each book that is on the shelf twice. */
    val onSelectDuplicates: () -> Unit = {},
    /** Covers or rows, which is remembered between sessions. */
    val layout: LibraryLayout = LibraryLayout.Grid,
    val onLayoutChange: (LibraryLayout) -> Unit = {},
) {
    val active: Boolean get() = selection.active

    companion object {
        val None = LibraryShelfUi()
    }
}
