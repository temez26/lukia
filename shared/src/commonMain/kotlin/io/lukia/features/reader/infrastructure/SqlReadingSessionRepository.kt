package io.lukia.features.reader.infrastructure

import io.lukia.core.domain.reading.ReadingSession
import io.lukia.core.domain.reading.ReadingSessionRepository
import io.lukia.core.infrastructure.db.LukiaDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlReadingSessionRepository(
    private val database: LukiaDatabase,
    /** Injectable so tests can run against a database deterministically. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ReadingSessionRepository {

    private val sessions = database.readingSessionQueries

    /**
     * The insert and the reading back of its id are one transaction, so two
     * books opened at once cannot hand the same row to both.
     */
    override suspend fun start(location: String, startedAt: Long): Long =
        withContext(dispatcher) {
            database.transactionWithResult {
                sessions.start(location = location, startedAt = startedAt)
                sessions.lastId().executeAsOne()
            }
        }

    override suspend fun extend(id: Long, durationMs: Long) = withContext(dispatcher) {
        sessions.extend(durationMs = durationMs, id = id)
        Unit
    }

    override suspend fun all(): List<ReadingSession> = withContext(dispatcher) {
        sessions.selectAll().executeAsList().map { row ->
            ReadingSession(
                location = row.location,
                startedAt = row.startedAt,
                durationMs = row.durationMs,
            )
        }
    }

    override suspend fun forget(location: String) = withContext(dispatcher) {
        sessions.deleteByLocation(location)
        Unit
    }
}
