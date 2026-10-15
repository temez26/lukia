package io.lukia.features.sync.application

import io.lukia.api.BookStateRequest
import io.lukia.api.BookStateResponse
import io.lukia.api.FavoritePart
import io.lukia.api.LayoutPart
import io.lukia.api.ProgressPart
import io.lukia.core.domain.document.ShelfChanges
import io.lukia.core.infrastructure.SyncedPart
import io.lukia.features.account.application.AccountSession
import io.lukia.features.sync.infrastructure.BookStateApi
import io.lukia.features.sync.infrastructure.BookStateStore
import io.lukia.features.sync.infrastructure.LocalBookState
import io.lukia.features.sync.infrastructure.PendingChange
import io.lukia.features.sync.infrastructure.SyncCursors

/**
 * Brings the reader's place in each book, how they read its PDF and whether it
 * is a favourite level with the account, both ways. See
 * docs/decisions/0003-offline-first-sync.md.
 *
 * Sends first and takes second. What this device changed goes up and the
 * server keeps whichever change was made later, part by part; then everything
 * the account changed since the last time comes down and is applied - except
 * where a change made here is still waiting to go up, which the next round
 * settles.
 */
class SyncBookStateUseCase(
    private val session: AccountSession,
    private val api: BookStateApi,
    private val store: BookStateStore,
    private val cursors: SyncCursors,
    private val changes: ShelfChanges,
) {
    suspend operator fun invoke() {
        if (!cursors.snapshotTaken) {
            store.snapshot()
            cursors.snapshotTaken = true
        }
        send()
        if (receive()) changes.announce()
    }

    private suspend fun send() {
        store.pending().groupBy { it.bookId }.forEach { (bookId, waiting) ->
            val state = store.stateOf(bookId)
            val request = state?.let { requestFor(it, waiting) }

            if (request != null) {
                session.authorized { address, token -> api.send(address, token, bookId, request) }
            }
            // Sent, or about a book or a part no longer here to send: either
            // way, nothing left to wait for.
            waiting.forEach { store.sent(it) }
        }
    }

    private fun requestFor(state: LocalBookState, waiting: List<PendingChange>) =
        BookStateRequest(
            progress = waiting.at(SyncedPart.Progress)?.let { change ->
                val (index, count) = state.pageIndex to state.pageCount
                if (index != null && count != null) ProgressPart(index, count, change.changedAt) else null
            },
            layout = waiting.at(SyncedPart.Layout)?.let { change ->
                state.layout?.let { LayoutPart(it, change.changedAt) }
            },
            favorite = waiting.at(SyncedPart.Favorite)?.let { FavoritePart(state.isFavorite, it.changedAt) },
        ).takeIf { it.progress != null || it.layout != null || it.favorite != null }

    /** Whether the shelf changed. */
    private suspend fun receive(): Boolean {
        var since = cursors.bookState()
        var changed = false

        do {
            val page = session.authorized { address, token -> api.changes(address, token, since) }
            page.items.forEach { changed = apply(it) || changed }
            since = page.cursor
            cursors.setBookState(since)
        } while (page.hasMore)

        return changed
    }

    /** A book this device does not have is passed over; its state waits on the account. */
    private suspend fun apply(remote: BookStateResponse): Boolean {
        val location = store.locationOf(remote.bookId) ?: return false
        var changed = false

        remote.progress?.takeUnless { store.isPending(SyncedPart.Progress, remote.bookId) }?.let {
            store.applyProgress(location, it.pageIndex, it.pageCount)
            changed = true
        }
        remote.layout?.takeUnless { store.isPending(SyncedPart.Layout, remote.bookId) }?.let {
            store.applyLayout(location, it.layout)
            changed = true
        }
        remote.favorite?.takeUnless { store.isPending(SyncedPart.Favorite, remote.bookId) }?.let {
            store.applyFavorite(location, it.isFavorite)
            changed = true
        }
        return changed
    }

    private fun List<PendingChange>.at(part: SyncedPart) = firstOrNull { it.part == part }
}
