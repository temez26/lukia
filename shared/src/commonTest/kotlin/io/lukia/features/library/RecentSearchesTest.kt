package io.lukia.features.library

import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.features.library.infrastructure.RecentSearchesRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The searches a reader has made, as the search tab offers them back. */
class RecentSearchesTest {

    private val store = FakeSettingsStore()
    private val repository = RecentSearchesRepository(store)

    private fun remembered() = repository.terms.value

    @Test
    fun theNewestSearchIsFirst() {
        repository.remember("dune")
        repository.remember("hobbit")

        assertEquals(listOf("hobbit", "dune"), remembered())
    }

    /**
     * One row per search, not one per time it was made - and spelled the way it
     * was last typed, since that is the spelling the reader just chose.
     */
    @Test
    fun searchingForTheSameThingAgainMovesItBackToTheTop() {
        repository.remember("dune")
        repository.remember("hobbit")
        repository.remember("Dune")

        assertEquals(listOf("Dune", "hobbit"), remembered())
    }

    @Test
    fun surroundingWhitespaceIsTrimmedAndBlanksAreNotRemembered() {
        repository.remember("  dune  ")
        repository.remember("   ")
        repository.remember("")

        assertEquals(listOf("dune"), remembered())
    }

    @Test
    fun onlyTheMostRecentHandfulIsKept() {
        (1..12).forEach { repository.remember("term $it") }

        val kept = remembered()

        assertEquals(8, kept.size)
        assertEquals("term 12", kept.first())
        assertEquals("term 5", kept.last())
    }

    @Test
    fun forgettingDropsOneAndClearingDropsThemAll() {
        repository.remember("dune")
        repository.remember("hobbit")

        repository.forget("DUNE")
        assertEquals(listOf("hobbit"), remembered())

        repository.clear()
        assertTrue(remembered().isEmpty())
    }

    /** A history that did not survive the app being closed would not be one. */
    @Test
    fun whatWasRememberedIsReadBackFromTheStore() {
        repository.remember("dune")
        repository.remember("hobbit")

        assertEquals(listOf("hobbit", "dune"), RecentSearchesRepository(store).terms.value)
    }

    /** An empty stored value splits into one empty string, which is not a search. */
    @Test
    fun anEmptyStoredValueIsNoSearchesRatherThanOneBlankOne() {
        store.putString("library.recentSearches", "")

        assertTrue(RecentSearchesRepository(store).terms.value.isEmpty())
    }
}
