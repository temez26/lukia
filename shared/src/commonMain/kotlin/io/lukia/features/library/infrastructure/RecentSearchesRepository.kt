package io.lukia.features.library.infrastructure

import io.lukia.core.domain.storage.SettingsStore
import io.lukia.core.infrastructure.PersistedSettings
import kotlinx.coroutines.flow.StateFlow

/**
 * How many searches are kept. Enough to cover the handful of books somebody is
 * going back and forth between, short enough that the list is still readable
 * without scrolling on a phone.
 */
private const val MaxRecentSearches = 8

/**
 * Terms are stored one per line. A search field is single-line, so a newline is
 * the one character a term cannot contain, which makes it the one separator that
 * needs no escaping.
 */
private const val Separator = "\n"

private const val KeyRecentSearches = "library.recentSearches"

/**
 * The searches somebody has actually made, newest first and remembered between
 * sessions.
 *
 * Apart from the shelf's own settings - see [LibrarySettingsRepository] - because
 * this is a history rather than a choice: nobody set it, it grows on its own, and
 * it is the one thing here a reader may want to delete.
 *
 * Held in the settings store rather than the database. It is eight short strings
 * that have to be on screen the instant the search tab opens, and a query for
 * them would arrive after the screen it belongs to.
 */
class RecentSearchesRepository(
    private val store: SettingsStore,
) {

    private val persisted = PersistedSettings(read()) {
        store.putString(KeyRecentSearches, it.joinToString(Separator))
    }

    val terms: StateFlow<List<String>> = persisted.state

    /**
     * Files [term] as the most recent search, or moves it back to the top if it
     * has been made before.
     *
     * Matched without regard to case, and the new spelling wins: searching for
     * "Dune" after "dune" should leave one row, spelled the way it was last
     * typed rather than the way it was first typed.
     */
    fun remember(term: String) {
        val trimmed = term.trim()
        if (trimmed.isEmpty()) return

        write(
            listOf(trimmed) + terms.value.filterNot { it.equals(trimmed, ignoreCase = true) },
        )
    }

    fun forget(term: String) {
        write(terms.value.filterNot { it.equals(term, ignoreCase = true) })
    }

    fun clear() = write(emptyList())

    private fun write(terms: List<String>) = persisted.set(terms.take(MaxRecentSearches))

    /**
     * Blank entries are dropped on the way in rather than trusted: an empty
     * stored value splits into one empty string, which would otherwise show as a
     * nameless row nothing happens when you press.
     */
    private fun read(): List<String> = store.getString(KeyRecentSearches)
        .orEmpty()
        .split(Separator)
        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .take(MaxRecentSearches)
}
