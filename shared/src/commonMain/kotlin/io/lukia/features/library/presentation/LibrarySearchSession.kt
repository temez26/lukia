package io.lukia.features.library.presentation

import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** How long the results outlive the screen that was watching them. */
private const val ResultsCacheMillis = 5_000L

/**
 * Looking for a book: what has been typed, and what the whole library has to
 * answer it with.
 *
 * Held apart from [LibraryViewModel] rather than added to it - the same bargain
 * [LibraryImportSession] and [LibrarySelectionSession] make - so that searching
 * is a thing with its own state rather than three more fields on the shelf.
 *
 * Deliberately separate from [LibraryQuery]'s own search term. That one narrows
 * the shelf, alongside the filters beside it; this one asks about every book
 * there is, and answering it must not also narrow the shelf the reader left
 * behind on another tab.
 */
@Stable
class LibrarySearchSession(
    /** The whole library, with its search keys already worked out. */
    library: StateFlow<List<LibraryBook>>,
    scope: CoroutineScope,
    /** Where the matching runs, which is not the thread that draws the results. */
    dispatcher: CoroutineDispatcher,
) {

    private val _term = MutableStateFlow("")
    val term: StateFlow<String> = _term.asStateFlow()

    val results: StateFlow<List<LibraryBook>> = combine(library, _term) { books, term ->
        withContext(dispatcher) { books.searching(term) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(ResultsCacheMillis), emptyList())

    fun update(term: String) {
        _term.value = term
    }
}
