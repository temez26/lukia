package io.lukia.features.sync.application

import io.lukia.features.library.application.HashLibraryUseCase

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
) {
    suspend operator fun invoke() {
        hashLibrary()
        pullLibrary()
        syncBookState()
        syncSessions()
    }
}
