package io.lukia.core.infrastructure

import io.lukia.core.infrastructure.db.LukiaDatabase
import kotlin.time.Clock

/**
 * The parts of a book whose changes go to a sync server, by the names the
 * outbox stores them under - see `SyncOutbox.sq`.
 */
enum class SyncedPart(val stored: String) {
    Progress("progress"),
    Layout("layout"),
    Favorite("favorite"),
    ;

    companion object {
        fun fromStored(stored: String): SyncedPart? = entries.firstOrNull { it.stored == stored }
    }
}

/**
 * Notes, inside whatever transaction is open, that [part] of the book at
 * [location] changed just now - see `SyncOutbox.sq`. Called by the stores that
 * write those parts, in the same transaction as the write, so a change and its
 * note are never one without the other.
 */
fun LukiaDatabase.noteChange(
    part: SyncedPart,
    location: String,
    changedAt: Long = Clock.System.now().toEpochMilliseconds(),
) {
    syncOutboxQueries.record(kind = part.stored, changedAt = changedAt, location = location)
}
