package io.lukia.features.library.presentation.sessions

import androidx.compose.runtime.Stable
import io.lukia.features.library.infrastructure.RecentSearchesRepository
import io.lukia.features.library.presentation.model.LibraryBook
import io.lukia.features.library.presentation.model.searching
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
 * Held apart from [LibraryViewModel], the bargain [LibraryImportSession] and
 * [LibrarySelectionSession] make, so searching is a thing with its own state
 * rather than four more fields on the shelf.
 *
 * Deliberately separate from [LibraryQuery]'s search term: that one narrows the
 * shelf alongside the filters, while this asks about every book there is and
 * must not narrow the shelf left behind on another tab.
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
     * Called on the keyboard's search key and again when a result is opened -
     * the two moments a search is finished rather than half typed. Recording
     * every keystroke would fill the history with every prefix on the way.
     *
     * Doing it twice for one search costs nothing: the term is already at the
     * top, and putting it there again leaves it where it is.
     */
    fun submit() = recentSearches.remember(_term.value)

    fun forget(term: String) = recentSearches.forget(term)

    fun clearRecent() = recentSearches.clear()
}
