package io.lukia.features.translate.infrastructure

import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.testDatabase
import io.lukia.features.translate.infrastructure.wiktionary.CachedPage
import io.lukia.features.translate.infrastructure.wiktionary.SqlWiktionaryPageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Through real SQL rather than a stand-in, because what is worth proving here is
 * the SQL - the same reasoning as [SqlTranslationCacheTest].
 *
 * The case this table exists for is the third one: a wiki having no page for a
 * word is an answer, and it is the answer that costs the most to forget. A word
 * missing from the reader's own wiki is looked up on the English one as well, so
 * re-asking a miss is two requests rather than one.
 */
class SqlWiktionaryPageCacheTest {

    @Test
    fun aStoredPageComesBackAsItWentIn() = runTest {
        val cache = cache()

        cache.put("fi", "talo", "== Suomi ==\nasuinrakennus")

        assertEquals(
            "== Suomi ==\nasuinrakennus",
            assertIs<CachedPage.Text>(cache.get("fi", "talo")).extract,
        )
    }

    @Test
    fun aWordNobodyHasLookedUpIsNotThere() = runTest {
        assertNull(cache().get("fi", "never asked about"))
    }

    /** A wiki with no page for a word has answered, and is not asked twice. */
    @Test
    fun aWikiWithNoPageForAWordIsRememberedAsHavingNone() = runTest {
        val cache = cache()

        cache.put("fi", "Juoksi", extract = null)

        assertEquals(CachedPage.NoSuchPage, cache.get("fi", "Juoksi"))
    }

    /**
     * The Finnish Wiktionary's `skill` is written in Finnish and the English
     * one's is not, so they are two pages under one title.
     */
    @Test
    fun theSameTitleOnTwoWikisIsTwoPages() = runTest {
        val cache = cache()

        cache.put("fi", "skill", "taito")
        cache.put("en", "skill", "a learned ability")

        assertEquals("taito", assertIs<CachedPage.Text>(cache.get("fi", "skill")).extract)
        assertEquals(
            "a learned ability",
            assertIs<CachedPage.Text>(cache.get("en", "skill")).extract,
        )
    }

    /** A page read again replaces what was there, not adds to it. */
    @Test
    fun storingTheSamePageTwiceKeepsTheNewerOne() = runTest {
        val cache = cache()

        cache.put("fi", "talo", "First.")
        cache.put("fi", "talo", "Second.")

        assertEquals("Second.", assertIs<CachedPage.Text>(cache.get("fi", "talo")).extract)
    }

    /**
     * A cache nobody trims is a file that only grows. Written against the
     * database rather than the class so the ceiling can be reached without
     * storing a thousand rows through it.
     */
    @Test
    fun theTableIsTrimmedOnceItHasOutgrownItsBudget() = runTest {
        val database = testDatabase()
        val cache = SqlWiktionaryPageCache(database, Dispatchers.Unconfined)
        val queries = database.wiktionaryPageCacheQueries

        repeat(1_001) { index ->
            queries.put(wiki = "fi", title = "word-$index", extract = "Page $index")
        }

        cache.put("fi", "one more", "The one that tips it over.")

        assertEquals(902L, queries.count().executeAsOne())
    }

    /**
     * What falls out is what nobody came back to. A word looked up again is a
     * word the reader is still learning.
     */
    @Test
    fun readingAPageKeepsItAheadOfOneNobodyHasReturnedTo() = runTest {
        val database = testDatabase()
        val cache = SqlWiktionaryPageCache(database, Dispatchers.Unconfined)

        cache.put("fi", "older", "Older.")
        cache.put("fi", "newer", "Newer.")

        // Coming back to the older one is what puts it in front.
        cache.get("fi", "older")

        database.wiktionaryPageCacheQueries.trimLeastRecentlyUsed(count = 1)

        assertNotNull(cache.get("fi", "older"))
        assertNull(cache.get("fi", "newer"))
    }

    private fun cache(database: LukiaDatabase = testDatabase()) =
        SqlWiktionaryPageCache(database, Dispatchers.Unconfined)
}
