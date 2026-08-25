package io.lukia.features.library.presentation

import androidx.compose.runtime.Stable
import io.lukia.features.library.infrastructure.RecentSearchesRepository
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
 * Looking for a book: what has been typed, what the whole library has to answer
 * it with, and what has been looked for before.
 *
 * Held apart from [LibraryViewModel] rather than added to it - the same bargain
 * [LibraryImportSession] and [LibrarySelectionSession] make - so that searching
 * is a thing with its own state rather than four more fields on the shelf.
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
    private val recentSearches: RecentSearchesRepository,
) {

    private val _term = MutableStateFlow("")
    val term: StateFlow<String> = _term.asStateFlow()

    val results: StateFlow<List<LibraryBook>> = combine(library, _term) { books, term ->
        withContext(dispatcher) { books.searching(term) }
    }.stateIn(scope, SharingStarted.WhileSubscribed(ResultsCacheMillis), emptyList())

    /** What has been searched for before, newest first - see [RecentSearchesRepository]. */
    val recent: StateFlow<List<String>> = recentSearches.terms

    fun update(term: String) {
        _term.value = term
    }

    /**
     * Files what is currently typed as a search that was actually made.
     *
     * Called when the reader presses the keyboard's search key, and again when
     * they open one of the results - the two moments a search is finished rather
     * than half typed. Recording every keystroke instead would fill the history
     * with every prefix of the word on the way to it.
     *
     * Doing it twice for one search costs nothing: the term is already at the
     * top of the list, and putting it there again leaves it exactly where it is.
     */
    fun submit() = recentSearches.remember(_term.value)

    fun forget(term: String) = recentSearches.forget(term)

    fun clearRecent() = recentSearches.clear()
}
