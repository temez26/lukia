package io.lukia.server.reading

import io.lukia.api.ChangePage
import io.lukia.api.SessionRequest
import io.lukia.api.SessionResponse
import io.lukia.server.db.Reading_session
import io.lukia.server.db.ServerDatabase
import io.lukia.server.http.changePage
import java.util.UUID
import kotlin.time.Duration.Companion.hours

/**
 * One account's reading sessions - append-only, and safe to send any number of
 * times. See docs/decisions/0003-offline-first-sync.md.
 */
class SessionService(private val database: ServerDatabase) {

    private val sessions get() = database.readingSessionQueries

    /**
     * Records the session, or lengthens it if it is already recorded. Its book,
     * start and title are the first send's: a sitting does not move.
     */
    fun record(accountId: Long, id: String, request: SessionRequest): SessionResponse {
        validate(id, request)

        return database.transactionWithResult {
            sessions.insertIfAbsent(
                accountId,
                id,
                request.bookId,
                request.title?.take(MaxTitleLength),
                request.startedAt,
                request.durationMs,
            )
            sessions.extend(request.durationMs, accountId, id)
            sessions.selectOne(accountId, id).executeAsOne().toResponse()
        }
    }

    fun changesSince(accountId: Long, since: Long, limit: Int): ChangePage<SessionResponse> = changePage(
        since,
        limit,
        fetch = { after, count -> sessions.selectSince(accountId, after, count).executeAsList() },
        seqOf = Reading_session::seq,
        toItem = { it.toResponse() },
    )
}

private fun validate(id: String, request: SessionRequest) {
    invalidUnless(isUuid(id)) { "A session is named by the UUID its device made for it." }
    requireBookId(request.bookId)
    invalidUnless(request.startedAt > 0) { "Give the time the session started." }
    invalidUnless(request.durationMs in 0..MaxDuration.inWholeMilliseconds) {
        "A session lasts from no time to a day."
    }
}

private fun isUuid(id: String) = id.length == UuidLength && runCatching { UUID.fromString(id) }.isSuccess

private fun Reading_session.toResponse() = SessionResponse(
    id = id,
    bookId = bookId,
    startedAt = startedAt,
    durationMs = durationMs,
    title = title,
)

/** Longer than anybody reads in one sitting; anything over it is a clock or a bug, not a reader. */
private val MaxDuration = 24.hours

private const val UuidLength = 36

/** Titles are for showing, and the longest in a real library are a few hundred characters. */
private const val MaxTitleLength = 500
