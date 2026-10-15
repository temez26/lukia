package io.lukia.features.sync.infrastructure

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import io.lukia.core.infrastructure.SyncedPart
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.ioDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/** A change waiting in the outbox - see `SyncOutbox.sq`. */
data class PendingChange(val part: SyncedPart, val bookId: String, val changedAt: Long)

/** What this device holds for one book, read when its changes are sent. */
data class LocalBookState(
    val location: String,
    val isFavorite: Boolean,
    val pageIndex: Int?,
    val pageCount: Int?,
    val layout: String?,
)

/**
 * The outbox and the shelf as the sync engine reads and writes them.
 *
 * Writes that come from the server go straight to the shelf and never through
 * the outbox, so nothing pulled is ever sent back as though it were this
 * device's own change.
 */
class BookStateStore(
    private val database: LukiaDatabase,
    private val dispatcher: CoroutineDispatcher = ioDispatcher(),
) {
    private val outbox get() = database.syncOutboxQueries

    /** How many changes are waiting, as it changes - what the sync engine sends on. */
    val waiting: Flow<Long> = outbox.count().asFlow().mapToOne(dispatcher)

    suspend fun pending(): List<PendingChange> = withContext(dispatcher) {
        outbox.selectAll().executeAsList().mapNotNull { row ->
            SyncedPart.fromStored(row.kind)?.let { PendingChange(it, row.key, row.changedAt) }
        }
    }

    suspend fun stateOf(bookId: String): LocalBookState? = withContext(dispatcher) {
        outbox.selectBookState(bookId).executeAsOneOrNull()?.let {
            LocalBookState(it.location, it.isFavorite, it.pageIndex?.toInt(), it.pageCount?.toInt(), it.layout)
        }
    }

    suspend fun sent(change: PendingChange) = withContext(dispatcher) {
        outbox.sent(change.part.stored, change.bookId, change.changedAt)
        Unit
    }

    suspend fun isPending(part: SyncedPart, bookId: String): Boolean = withContext(dispatcher) {
        outbox.isPending(part.stored, bookId).executeAsOne()
    }

    suspend fun locationOf(bookId: String): String? = withContext(dispatcher) {
        database.documentQueries.selectLocationByHash(bookId).executeAsOneOrNull()
    }

    suspend fun applyProgress(location: String, pageIndex: Int, pageCount: Int) = withContext(dispatcher) {
        database.readingPositionQueries.put(location, pageIndex.toLong(), pageCount.toLong())
        Unit
    }

    suspend fun applyLayout(location: String, layout: String) = withContext(dispatcher) {
        database.readerLayoutQueries.put(location, layout)
        Unit
    }

    suspend fun applyFavorite(location: String, isFavorite: Boolean) = withContext(dispatcher) {
        database.documentQueries.setFavorite(isFavorite, location)
        Unit
    }

    /** Queues everything the device already knows, dated so the account's own changes win - see `SyncOutbox.sq`. */
    suspend fun snapshot() = withContext(dispatcher) {
        database.transaction {
            outbox.snapshotProgress()
            outbox.snapshotLayout()
            outbox.snapshotFavorite()
        }
    }
}
