package io.lukia.features.library.presentation

import androidx.compose.runtime.Stable

/**
 * Everything the shelf's selection mode is and does, in one value.
 *
 * Ten parameters that always arrive together and are only ever read together,
 * gathered so that the screen's own signature stays about the screen - the same
 * bargain `ReaderTranslationUi` makes for the reader's translation controls.
 */
@Stable
class LibrarySelectionUi(
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
) {
    val active: Boolean get() = selection.active

    companion object {
        val None = LibrarySelectionUi()
    }
}
