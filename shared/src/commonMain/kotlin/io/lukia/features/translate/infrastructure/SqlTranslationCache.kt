package io.lukia.features.translate.infrastructure

import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.ioDispatcher
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationCache
import io.lukia.features.translate.domain.TranslationCacheKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * How many translated passages are kept.
 *
 * A passage and its note are a few hundred bytes, so this is a couple of
 * megabytes at the outside - nothing beside the covers the same database
 * already holds, and far more than a reader gets through in a book. The point
 * of the ceiling is not the disk; it is that a cache nobody ever trims is a
 * file that only grows.
 */
private const val MAX_ENTRIES = 5_000L

/**
 * How much is dropped once the ceiling is reached.
 *
 * A tenth at a time rather than one entry per insert: trimming is a delete over
 * an index, and paying for it on every single translation to stay exactly at the
 * limit would be the cache costing more than it saves.
 */
private const val TRIM_BATCH = MAX_ENTRIES / 10

/**
 * The store of passages already translated, in the app's own database.
 *
 * Everything runs off the calling thread, like the rest of this app's storage:
 * a cache that stuttered the page it was meant to speed up would be worse than
 * no cache.
 */
class SqlTranslationCache(
    database: LukiaDatabase,
    /** Injectable so tests can run against a database deterministically. */
    private val dispatcher: CoroutineDispatcher = ioDispatcher(),
) : TranslationCache {

    private val cache = database.translationCacheQueries

    override suspend fun get(key: TranslationCacheKey): Translation? = withContext(dispatcher) {
        val stored = cache.selectByKey(key.value).executeAsOneOrNull() ?: return@withContext null

        // Reading is what keeps an entry alive. A sentence somebody keeps coming
        // back to should outlive the hundred they passed over once.
        cache.touch(key.value)

        Translation(text = stored.translation, note = stored.note)
    }

    override suspend fun put(key: TranslationCacheKey, translation: Translation) =
        withContext(dispatcher) {
            cache.put(
                cacheKey = key.value,
                translation = translation.text,
                note = translation.note,
            )

            if (cache.count().executeAsOne() > MAX_ENTRIES) {
                cache.trimLeastRecentlyUsed(TRIM_BATCH)
            }
        }
}
