package io.lukia.features.translate.infrastructure

import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.testDatabase
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationCacheKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Through real SQL rather than a stand-in, because what is worth proving here is
 * the SQL: that a row comes back as it went in, and that a table which is only
 * ever added to eventually drops something.
 */
class SqlTranslationCacheTest {

    @Test
    fun aStoredTranslationComesBackAsItWentIn() = runTest {
        val cache = cache()

        cache.put(key("a"), Translation("Kutsukaa minua Ismaeliksi.", note = "A remark."))

        val stored = assertNotNull(cache.get(key("a")))
        assertEquals("Kutsukaa minua Ismaeliksi.", stored.text)
        assertEquals("A remark.", stored.note)
    }

    /** Most sentences translate without anything worth saying about them. */
    @Test
    fun aTranslationWithNoRemarkComesBackWithout() = runTest {
        val cache = cache()

        cache.put(key("a"), Translation("Kutsukaa minua Ismaeliksi."))

        assertNull(assertNotNull(cache.get(key("a"))).note)
    }

    @Test
    fun aPassageNobodyHasTranslatedIsNotThere() = runTest {
        assertNull(cache().get(key("never asked about")))
    }

    /** The same passage translated again replaces what was there, not adds to it. */
    @Test
    fun storingTheSameKeyTwiceKeepsTheNewerAnswer() = runTest {
        val cache = cache()

        cache.put(key("a"), Translation("First."))
        cache.put(key("a"), Translation("Second."))

        assertEquals("Second.", assertNotNull(cache.get(key("a"))).text)
    }

    @Test
    fun twoPassagesAreKeptApart() = runTest {
        val cache = cache()

        cache.put(key("a"), Translation("First."))
        cache.put(key("b"), Translation("Second."))

        assertEquals("First.", assertNotNull(cache.get(key("a"))).text)
        assertEquals("Second.", assertNotNull(cache.get(key("b"))).text)
    }

    /**
     * A cache nobody trims is a file that only grows. Written against the
     * database rather than the class so the ceiling can be reached without
     * storing five thousand rows through it.
     */
    @Test
    fun theTableIsTrimmedOnceItHasOutgrownItsBudget() = runTest {
        val database = testDatabase()
        val cache = SqlTranslationCache(database, Dispatchers.Unconfined)
        val queries = database.translationCacheQueries

        repeat(5_001) { index ->
            queries.put(
                cacheKey = "key-$index",
                translation = "Translation $index",
                note = null,
            )
        }

        cache.put(key("one more"), Translation("The one that tips it over."))

        val remaining = queries.count().executeAsOne()
        assertEquals(4_502L, remaining)
    }

    /**
     * What falls out is what nobody came back to. A sentence read again is a
     * sentence somebody is still working on.
     */
    @Test
    fun readingAPassageKeepsItAheadOfOneNobodyHasReturnedTo() = runTest {
        val database = testDatabase()
        val cache = SqlTranslationCache(database, Dispatchers.Unconfined)
        val queries = database.translationCacheQueries

        cache.put(key("older"), Translation("Older."))
        cache.put(key("newer"), Translation("Newer."))

        // Coming back to the older one is what puts it in front.
        cache.get(key("older"))

        queries.trimLeastRecentlyUsed(count = 1)

        assertNotNull(cache.get(key("older")))
        assertNull(cache.get(key("newer")))
    }

    private fun cache(database: LukiaDatabase = testDatabase()) =
        SqlTranslationCache(database, Dispatchers.Unconfined)

    private fun key(value: String) = TranslationCacheKey(value)
}
