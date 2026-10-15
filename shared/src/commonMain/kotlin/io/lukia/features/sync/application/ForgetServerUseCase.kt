package io.lukia.features.sync.application

import io.lukia.core.domain.document.ShelfChanges
import io.lukia.features.library.domain.ServerShelf
import io.lukia.features.sync.infrastructure.SyncCursors

/**
 * Lets go of the server once the device signs out: server books never
 * downloaded leave the shelf, nothing is marked as on a server, and the next
 * sign-in catches up from the beginning.
 *
 * Downloaded books, and every position, layout and favourite on this device,
 * stay - they are the reader's, whichever account they came through.
 */
class ForgetServerUseCase(
    private val shelf: ServerShelf,
    private val cursors: SyncCursors,
    private val changes: ShelfChanges,
) {
    /** Only once there was something to forget, so a device that never signed in does no work at launch. */
    suspend operator fun invoke() {
        if (!cursors.anySet) return
        shelf.forgetServer()
        cursors.clear()
        changes.announce()
    }
}
