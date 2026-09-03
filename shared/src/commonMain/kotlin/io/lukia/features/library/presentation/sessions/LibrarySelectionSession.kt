package io.lukia.features.library.presentation.sessions

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.reading.ReadingProgressRepository
import io.lukia.core.domain.runCatchingApp
import io.lukia.features.library.application.RefreshDocumentUseCase
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.library.application.SetFavoriteUseCase
import io.lukia.features.library.presentation.model.BulkAction
import io.lukia.features.library.presentation.model.BulkOperation
import io.lukia.features.library.presentation.model.LibraryMessage
import io.lukia.features.library.presentation.model.LibrarySelection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Selecting several books at once, and doing one thing to all of them.
 *
 * Held apart from [LibraryViewModel] rather than added to it, the same way the
 * reader keeps its translations apart: this is a mode with its own state, its
 * own actions and its own idea of when it is busy, and threading all of that
 * through the view model would leave neither readable.
 *
 * Every action here is a loop over the use case that already does it to one
 * book. That is deliberate - a bulk delete is not a different kind of delete,
 * and the moment it becomes one is the moment the two can disagree about what
 * removing a book means.
 */
class LibrarySelectionSession(
    private val setFavoriteUseCase: SetFavoriteUseCase,
    private val refreshDocumentUseCase: RefreshDocumentUseCase,
    private val removeDocumentUseCase: RemoveDocumentUseCase,
    private val progressRepository: ReadingProgressRepository,
    private val scope: CoroutineScope,
) {

    private val _selection = MutableStateFlow(LibrarySelection.None)
    val selection: StateFlow<LibrarySelection> = _selection.asStateFlow()

    private val _running = MutableStateFlow<BulkAction?>(null)
    val running: StateFlow<BulkAction?> = _running.asStateFlow()

    /**
     * Starts picking books, optionally with some already ticked - which is how
     * the shelf's upkeep hands over what it found, so the reader ends up looking
     * at the books in question rather than at a report about them.
     */
    fun start(locations: Set<String> = emptySet()) {
        _selection.value = LibrarySelection(active = true, locations = locations)
    }

    fun stop() {
        _selection.value = LibrarySelection.None
    }

    fun toggle(location: String) {
        _selection.update { current ->
            val locations = if (location in current) {
                current.locations - location
            } else {
                current.locations + location
            }

            current.copy(locations = locations)
        }
    }

    /**
     * Ticks everything on the shelf, or clears it where everything already is.
     *
     * One control rather than two, taking what the shelf is currently showing
     * rather than the whole library: a reader who has narrowed to one author and
     * pressed Select All has selected that author, not everything they own.
     */
    fun toggleAll(locations: List<String>) {
        _selection.update { current ->
            val all = locations.toSet()

            current.copy(locations = if (current.locations.containsAll(all)) emptySet() else all)
        }
    }

    /**
     * Reads each selected book again, for covers and titles an import could not
     * make sense of - the same thing the single-book action does, done to all of
     * them.
     */
    fun refresh(onDone: (LibraryMessage) -> Unit) =
        run(operation = BulkOperation.Refresh, onDone = onDone) { location ->
            refreshDocumentUseCase(location).getOrThrow()
        }

    /**
     * Favourites everything selected, unless all of it already is - in which
     * case this is how a reader takes a whole shelf back off their favourites.
     */
    fun setFavorite(books: List<DocumentInfo>, onDone: (LibraryMessage) -> Unit) {
        val selected = books.filter { it.document.location in _selection.value }
        val favorite = !selected.all { it.isFavorite }
        val operation = if (favorite) BulkOperation.Favourite else BulkOperation.Unfavourite

        run(operation = operation, onDone = onDone) { location ->
            setFavoriteUseCase(location, favorite)
        }
    }

    /** Forgets where the reader had got to, putting the books back to unread. */
    fun resetProgress(onDone: (LibraryMessage) -> Unit) =
        run(operation = BulkOperation.Reset, onDone = onDone) { location ->
            progressRepository.forget(location)
        }

    /**
     * Drops the books from the library. The files on disk are left alone - see
     * [RemoveDocumentUseCase], which is what this is a loop over.
     */
    fun delete(onDone: (LibraryMessage) -> Unit) =
        run(operation = BulkOperation.Delete, onDone = onDone) { location ->
            removeDocumentUseCase(location)
        }

    /**
     * Runs [action] over everything selected, counting as it goes, and leaves
     * selection mode when it is done.
     *
     * A book the action fails on is counted and passed over rather than stopping
     * the rest: a shelf of forty where the third file has gone missing should
     * still have the other thirty-nine done, and the count at the end is what
     * says so.
     */
    private fun run(
        operation: BulkOperation,
        onDone: (LibraryMessage) -> Unit,
        action: suspend (location: String) -> Unit,
    ) {
        val locations = _selection.value.locations.toList()

        if (locations.isEmpty() || _running.value != null) return

        scope.launch {
            var done = 0
            var failed = 0

            _running.value = BulkAction(operation = operation, completed = 0, total = locations.size)

            locations.forEach { location ->
                runCatchingApp { action(location) }
                    .onSuccess { done++ }
                    .onFailure { failed++ }

                _running.update { it?.copy(completed = done + failed) }
            }

            _running.value = null
            stop()

            onDone(LibraryMessage.BulkDone(operation = operation, done = done, failed = failed))
        }
    }
}
