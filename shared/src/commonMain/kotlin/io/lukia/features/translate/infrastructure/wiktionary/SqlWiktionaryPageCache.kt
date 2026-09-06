package io.lukia.features.translate.infrastructure.wiktionary

import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.ioDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * How many wiki pages are kept.
 *
 * A page's text is a few kilobytes rather than the few hundred bytes a
 * translation is, so this ceiling is far lower than the translation cache's and
 * lands in the same handful of megabytes. Well past the vocabulary of one book,
 * which is what it is for.
 */
private const val MAX_PAGES = 1_000L

/** How much is dropped once the ceiling is reached - see `SqlTranslationCache`. */
private const val TRIM_BATCH = MAX_PAGES / 10

/**
 * The store of wiki pages already read, in the app's own database.
 *
 * Everything runs off the calling thread, like the rest of this app's storage: a
 * cache that stuttered the panel it was meant to open faster would be worse than
 * no cache.
 */
internal class SqlWiktionaryPageCache(
    database: LukiaDatabase,
    /** Injectable so tests can run against a database deterministically. */
    private val dispatcher: CoroutineDispatcher = ioDispatcher(),
) : WiktionaryPageCache {

    private val pages = database.wiktionaryPageCacheQueries

    override suspend fun get(wiki: String, title: String): CachedPage? = withContext(dispatcher) {
        val stored = pages.selectPage(wiki, title).executeAsOneOrNull() ?: return@withContext null

        // Reading is what keeps a page alive. A word somebody keeps coming back
        // to should outlive the hundred they looked up once.
        pages.touch(wiki, title)

        stored.extract?.let { CachedPage.Text(it) } ?: CachedPage.NoSuchPage
    }

    override suspend fun put(wiki: String, title: String, extract: String?) =
        withContext(dispatcher) {
            pages.put(wiki = wiki, title = title, extract = extract)

            if (pages.count().executeAsOne() > MAX_PAGES) {
                pages.trimLeastRecentlyUsed(TRIM_BATCH)
            }
        }
}
