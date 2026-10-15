package io.lukia.features.library.presentation.sessions

import io.lukia.core.presentation.dataOrNull
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.model.BulkAction
import io.lukia.features.library.presentation.model.LibrarySelection
import io.lukia.features.library.presentation.model.upkeep
import kotlinx.coroutines.flow.StateFlow

/**
 * Selection mode's surface, kept beside the ViewModel rather than inside it.
 *
 * Every one of these hands straight on to [LibrarySelectionSession], which is
 * where ticking books off a shelf already lives. Gathering the pass-throughs
 * here keeps the ViewModel about the library's own state, and puts the mode's
 * whole surface - what is picked, what is running, and everything that can be
 * done to what is picked - on one page.
 */
val LibraryViewModel.selection: StateFlow<LibrarySelection>
    get() = selectionSession.selection

/** What a bulk action is doing right now, or null when none is running. */
val LibraryViewModel.bulkAction: StateFlow<BulkAction?>
    get() = selectionSession.running

fun LibraryViewModel.startSelecting() = selectionSession.start()

fun LibraryViewModel.stopSelecting() = selectionSession.stop()

fun LibraryViewModel.toggleSelected(location: String) = selectionSession.toggle(location)

/**
 * Ticks what the shelf is showing rather than the whole library: a reader who
 * has narrowed to one author and pressed Select All has selected that author,
 * and being handed everything they own instead would be the app answering a
 * question nobody asked.
 */
fun LibraryViewModel.toggleAllSelected() =
    selectionSession.toggleAll(books.value.map { it.location })

/**
 * Ticks the books whose files have gone, and leaves the reader looking at them.
 *
 * Worked out from the shelf as it stands rather than read from [upkeep], which
 * is shared only while the menu showing its counts is on screen - an action must
 * act on the library, not on whether anybody was watching it.
 */
fun LibraryViewModel.selectMissing() = selectionSession.start(books.value.upkeep().missing)

/** Ticks every copy but the first of each book that is on the shelf twice. */
fun LibraryViewModel.selectDuplicates() = selectionSession.start(books.value.upkeep().duplicates)

fun LibraryViewModel.refreshSelected() = selectionSession.refresh(::onBulkDone)

fun LibraryViewModel.setFavoriteOnSelected() =
    selectionSession.setFavorite(uiState.value.dataOrNull.orEmpty(), ::onBulkDone)

fun LibraryViewModel.resetProgressOnSelected() = selectionSession.resetProgress(::onBulkDone)

fun LibraryViewModel.deleteSelected() = selectionSession.delete(::onBulkDone)

fun LibraryViewModel.uploadSelected() =
    selectionSession.upload(uiState.value.dataOrNull.orEmpty(), uploader, ::onBulkDone)
