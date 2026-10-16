package io.lukia.server.reading

import io.lukia.api.BookStateRequest
import io.lukia.api.BookStateResponse
import io.lukia.api.ChangePage
import io.lukia.api.FavoritePart
import io.lukia.api.LayoutPart
import io.lukia.api.ProgressPart
import io.lukia.server.db.Book_state
import io.lukia.server.db.ServerDatabase
import io.lukia.server.events.ChangeEvents
import io.lukia.server.events.ChangeKind
import io.lukia.server.http.changePage
import java.time.Clock

/**
 * One account's progress, layout and favourite per book - last writer wins,
 * part by part. See docs/decisions/0003-offline-first-sync.md.
 *
 * Every call takes the account from the signed-in token, never from the
 * request, and every query is scoped by it.
 */
class BookStateService(
    private val database: ServerDatabase,
    private val clock: Clock,
    private val events: ChangeEvents = ChangeEvents(),
) {
    private val states get() = database.bookStateQueries

    /**
     * Applies whichever parts of [request] are newer than what is stored, and answers with the result.
     *
     * Devices are told only when something was actually stored: a part that
     * lost to a newer one, or a change sent again, would otherwise send every
     * device on the account round for nothing. [from] is the sign-in that sent
     * it, which is not told of its own change.
     */
    fun apply(accountId: Long, bookId: String, request: BookStateRequest, from: Long? = null): BookStateResponse {
        validate(bookId, request)

        val (stored, changed) = database.transactionWithResult {
            val seqBefore = states.selectOne(accountId, bookId).executeAsOneOrNull()?.seq
            states.ensure(accountId, bookId)
            request.progress?.let {
                states.setProgress(
                    it.pageIndex.toLong(),
                    it.pageCount.toLong(),
                    it.changedAt.notAfterNow(),
                    accountId,
                    bookId,
                )
            }
            request.layout?.let { states.setLayout(it.layout, it.changedAt.notAfterNow(), accountId, bookId) }
            request.favorite?.let {
                states.setFavorite(it.isFavorite, it.changedAt.notAfterNow(), accountId, bookId)
            }
            val after = states.selectOne(accountId, bookId).executeAsOne()
            after.toResponse() to (after.seq != seqBefore)
        }

        if (changed) events.announce(ChangeKind.BookState, accountId, from)
        return stored
    }

    fun changesSince(accountId: Long, since: Long, limit: Int): ChangePage<BookStateResponse> = changePage(
        since,
        limit,
        fetch = { after, count -> states.selectSince(accountId, after, count).executeAsList() },
        seqOf = Book_state::seq,
        toItem = { it.toResponse() },
    )

    /**
     * A change timed after now counts as now. Otherwise one device with its
     * clock years ahead would win every argument about that book for years.
     */
    private fun Long.notAfterNow(): Long = minOf(this, clock.millis())
}

private fun validate(bookId: String, request: BookStateRequest) {
    requireBookId(bookId)
    invalidUnless(request.progress != null || request.layout != null || request.favorite != null) {
        "Send at least one of progress, layout or favorite."
    }
    request.progress?.let {
        invalidUnless(it.pageIndex >= 0 && it.pageCount >= 0) { "A page index and count cannot be negative." }
        requireChangedAt(it.changedAt)
    }
    request.layout?.let {
        invalidUnless(it.layout.isNotBlank() && it.layout.length <= MaxLayoutLength) { "Name the layout." }
        requireChangedAt(it.changedAt)
    }
    request.favorite?.let { requireChangedAt(it.changedAt) }
}

/** Each part as it last won; a part is only ever written whole, so a half-filled one was never set. */
private fun Book_state.toResponse() = BookStateResponse(
    bookId = bookId,
    progress = if (pageIndex != null && pageCount != null && progressChangedAt != null) {
        ProgressPart(pageIndex.toInt(), pageCount.toInt(), progressChangedAt)
    } else {
        null
    },
    layout = if (layout != null && layoutChangedAt != null) LayoutPart(layout, layoutChangedAt) else null,
    favorite = if (isFavorite != null && favoriteChangedAt != null) {
        FavoritePart(isFavorite, favoriteChangedAt)
    } else {
        null
    },
)

private const val MaxLayoutLength = 32
