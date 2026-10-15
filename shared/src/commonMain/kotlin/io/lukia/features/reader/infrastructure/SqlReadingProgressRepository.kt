package io.lukia.features.reader.infrastructure

import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.domain.reading.ReadingProgressRepository
import io.lukia.core.infrastructure.SyncedPart
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.ioDispatcher
import io.lukia.core.infrastructure.noteChange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class SqlReadingProgressRepository(
    private val database: LukiaDatabase,
    /** Injectable so tests can run against a database deterministically. */
    private val dispatcher: CoroutineDispatcher = ioDispatcher(),
) : ReadingProgressRepository {

    private val positions = database.readingPositionQueries

    override suspend fun progressFor(location: String): ReadingProgress? =
        withContext(dispatcher) {
            positions.selectByLocation(location).executeAsOneOrNull()?.let {
                ReadingProgress(
                    pageIndex = it.pageIndex.toInt(),
                    pageCount = it.pageCount.toInt(),
                    readAt = it.readAt,
                )
            }
        }

    override suspend fun allProgress(): Map<String, ReadingProgress> =
        withContext(dispatcher) {
            positions.selectAll().executeAsList().associate { row ->
                row.location to ReadingProgress(
                    pageIndex = row.pageIndex.toInt(),
                    pageCount = row.pageCount.toInt(),
                    readAt = row.readAt,
                )
            }
        }

    /**
     * `readAt` is the statement's own doing rather than the caller's - see the
     * note on it in `ReadingPosition.sq` - so [ReadingProgress.readAt] is not
     * passed on here.
     */
    override suspend fun save(location: String, progress: ReadingProgress) =
        withContext(dispatcher) {
            database.transaction {
                positions.put(
                    location = location,
                    pageIndex = progress.pageIndex.toLong(),
                    pageCount = progress.pageCount.toLong(),
                )
                database.noteChange(SyncedPart.Progress, location)
            }
        }

    override suspend fun forget(location: String) = withContext(dispatcher) {
        positions.deleteByLocation(location)
        Unit
    }
}
