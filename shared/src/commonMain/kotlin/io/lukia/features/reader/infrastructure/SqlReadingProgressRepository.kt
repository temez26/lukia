package io.lukia.features.reader.infrastructure

import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.features.reader.domain.ReadingProgress
import io.lukia.features.reader.domain.ReadingProgressRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlReadingProgressRepository(
    database: LukiaDatabase,
    /** Injectable so tests can run against a database deterministically. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ReadingProgressRepository {

    private val positions = database.readingPositionQueries

    override suspend fun progressFor(location: String): ReadingProgress? =
        withContext(dispatcher) {
            positions.selectByLocation(location).executeAsOneOrNull()?.let {
                ReadingProgress(
                    pageIndex = it.pageIndex.toInt(),
                    pageCount = it.pageCount.toInt()
                )
            }
        }

    override suspend fun save(location: String, progress: ReadingProgress) =
        withContext(dispatcher) {
            positions.put(
                location = location,
                pageIndex = progress.pageIndex.toLong(),
                pageCount = progress.pageCount.toLong()
            )
            Unit
        }

    override suspend fun forget(location: String) = withContext(dispatcher) {
        positions.deleteByLocation(location)
        Unit
    }
}
