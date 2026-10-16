package io.lukia.features.sync.infrastructure

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import io.lukia.api.SessionRequest
import io.lukia.api.SessionResponse
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.ioDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A sitting the account has not heard all of, as it will be sent. */
data class UnsentSession(val id: String, val request: SessionRequest)

/**
 * Reading sessions as the sync engine reads and writes them - see the notes on
 * `sentDurationMs` and `deviceOnly` in `ReadingSession.sq`.
 */
class SessionStore(
    private val database: LukiaDatabase,
    private val dispatcher: CoroutineDispatcher = ioDispatcher(),
) {
    private val sessions get() = database.readingSessionQueries

    /** How many sittings are waiting to be sent, as it changes. */
    val waiting: Flow<Long> = sessions.countUnsent().asFlow().mapToOne(dispatcher)

    /**
     * How many sittings [unsent] with the same [afterId] would return, counted
     * again whenever the sittings change. [afterId] is asked each time, since
     * it moves when the reader answers the history question.
     */
    fun waitingAfter(afterId: () -> Long): Flow<Long> =
        sessions.maxId().asFlow().map {
            withContext(dispatcher) { sessions.countUnsentAfter(afterId()).executeAsOne() }
        }

    /** Sittings not fully sent, of those after [afterId] - all of them when it is zero. */
    suspend fun unsent(afterId: Long = 0): List<UnsentSession> = withContext(dispatcher) {
        sessions.selectUnsent(afterId).executeAsList().map { row ->
            UnsentSession(row.uuid, SessionRequest(row.contentHash, row.startedAt, row.durationMs, row.title))
        }
    }

    suspend fun markSent(id: String, durationMs: Long) = withContext(dispatcher) {
        sessions.markSent(sentDurationMs = durationMs, uuid = id)
        Unit
    }

    /** Whether any sitting up to [upToId] is one no account has heard of. */
    suspend fun hasUnsentHistory(upToId: Long): Boolean = withContext(dispatcher) {
        sessions.hasUnsentHistory(upToId).executeAsOne()
    }

    suspend fun latestId(): Long = withContext(dispatcher) { sessions.maxId().executeAsOne() }

    suspend fun keepOnDevice(upToId: Long) = withContext(dispatcher) {
        sessions.keepOnDevice(upToId)
        Unit
    }

    /** A sitting from another device, filed under this shelf's copy of its book or a stand-in for it. */
    suspend fun receive(session: SessionResponse) = withContext(dispatcher) {
        val location = database.documentQueries.selectLocationByHash(session.bookId).executeAsOneOrNull()
            ?: "$RemoteLocationPrefix${session.bookId}"

        database.transaction {
            sessions.insertFromServer(
                location = location,
                startedAt = session.startedAt,
                durationMs = session.durationMs,
                uuid = session.id,
                contentHash = session.bookId,
                title = session.title,
            )
            sessions.extendFromServer(durationMs = session.durationMs, uuid = session.id)
        }
    }

    private companion object {
        /**
         * Where a sitting in a book this device has never had is filed. Counted
         * by the statistics like any other, and never mistaken for a book on
         * the shelf: no shelf location has this shape.
         */
        const val RemoteLocationPrefix = "remote:"
    }
}
