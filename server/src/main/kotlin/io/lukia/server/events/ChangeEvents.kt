package io.lukia.server.events

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What changed, as a device listening for changes is told: enough to know to catch up, nothing more. */
enum class ChangeKind(val wire: String) {
    /** A book came into or left the shared library - every account's concern. */
    Library("library"),

    /** One account's place, layout or favourite in a book. */
    BookState("state"),

    /** One account's reading sessions. */
    Sessions("sessions"),
}

/** A change, and whose: [accountId] null is the shared library, which is everybody's. */
data class Change(val kind: ChangeKind, val accountId: Long?)

/**
 * Tells the devices listening that something changed, so they catch up now
 * rather than at their next check. A hint, not the change itself: a device
 * that misses one is no worse off than before, because it still asks for what
 * changed since its cursor.
 *
 * In memory and best effort - a burst that outruns a slow listener drops the
 * oldest, which costs nothing, since one hint is as good as many.
 */
class ChangeEvents {

    private val changes = MutableSharedFlow<Change>(
        extraBufferCapacity = Buffer,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val all: SharedFlow<Change> = changes.asSharedFlow()

    /** Safe from the blocking threads the services run on: it never waits. */
    fun announce(kind: ChangeKind, accountId: Long? = null) {
        changes.tryEmit(Change(kind, accountId))
    }

    private companion object {
        const val Buffer = 64
    }
}
