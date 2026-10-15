package io.lukia.features.sync.application

import io.lukia.features.library.application.HashLibraryUseCase
import io.lukia.features.sync.infrastructure.SyncCursors

/**
 * One full round with the server, in the order each step needs the last:
 * books are hashed, so one already on the device is recognised as the
 * server's; the library comes next, so a book's state has a book to land on;
 * then the reader's place and choices, then their sessions.
 */
class SyncRoundUseCase(
    private val hashLibrary: HashLibraryUseCase,
    private val pullLibrary: PullServerLibraryUseCase,
    private val syncBookState: SyncBookStateUseCase,
    private val syncSessions: SyncSessionsUseCase,
    private val cursors: SyncCursors,
) {
    suspend operator fun invoke() {
        val hashed = hashLibrary() > 0
        val added = pullLibrary()
        // The account's state for a book this device did not have was passed
        // over when it came down; now that the book may be here, ask again.
        if (hashed || added) cursors.setBookState(0)
        syncBookState()
        syncSessions()
    }
}
