package io.lukia.server.events

import io.lukia.server.accounts.SignedInAccount
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

/**
 * A change, and whose: [accountId] null is the shared library, which is
 * everybody's. [from] is the sign-in - one device - that made it, when a
 * device did.
 */
data class Change(val kind: ChangeKind, val accountId: Long?, val from: Long? = null)

/**
 * Whether the device signed in as [account] should hear of this change: one
 * that is the shared library's or its account's, and that some other device
 * made. The device that made a change already has it, and telling it anyway
 * sent it round for a full sync for every page it turned.
 */
fun Change.isFor(account: SignedInAccount): Boolean =
    (accountId == null || accountId == account.id) && from != account.familyId

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
    fun announce(kind: ChangeKind, accountId: Long? = null, from: Long? = null) {
        changes.tryEmit(Change(kind, accountId, from))
    }

    private companion object {
        const val Buffer = 64
    }
}
