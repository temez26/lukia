package io.lukia.features.sync

import io.lukia.core.domain.document.ShelfChanges
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.core.infrastructure.JvmBookFileStore
import io.lukia.core.infrastructure.SyncedPart
import io.lukia.core.infrastructure.testDatabase
import io.lukia.features.account.InMemoryAccountStore
import io.lukia.features.account.TestSyncServer
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.infrastructure.KtorServerApi
import io.lukia.features.account.infrastructure.ServerHttp
import io.lukia.features.account.infrastructure.serverHttpClient
import io.lukia.features.library.application.HashLibraryUseCase
import io.lukia.features.library.infrastructure.SqlContentHashStore
import io.lukia.features.library.infrastructure.SqlLibraryRepository
import io.lukia.features.library.infrastructure.SqlServerShelf
import io.lukia.features.reader.infrastructure.SqlPdfLayoutRepository
import io.lukia.features.reader.infrastructure.SqlReadingProgressRepository
import io.lukia.features.reader.infrastructure.SqlReadingSessionRepository
import io.lukia.features.sync.application.PullServerLibraryUseCase
import io.lukia.features.sync.application.SyncBookStateUseCase
import io.lukia.features.sync.application.SyncRoundUseCase
import io.lukia.features.sync.application.SyncSessionsUseCase
import io.lukia.features.sync.infrastructure.BookStateApi
import io.lukia.features.sync.infrastructure.BookStateStore
import io.lukia.features.sync.infrastructure.HistoryChoice
import io.lukia.features.sync.infrastructure.ServerLibraryApi
import io.lukia.features.sync.infrastructure.SessionApi
import io.lukia.features.sync.infrastructure.SessionStore
import io.lukia.features.sync.infrastructure.SyncCursors
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files

/**
 * One device signed in to [server]: its own database, shelf, reader stores and
 * sync engine, everything real but the clock it is told to use for a change.
 */
class SyncedDevice(private val server: TestSyncServer) : AutoCloseable {
    val database = testDatabase()
    private val downloads = Files.createTempDirectory("lukia-device").toFile()
    private val settings = FakeSettingsStore()
    val session = AccountSession(KtorServerApi(serverHttpClient()), InMemoryAccountStore())
    val library = SqlLibraryRepository(database, Dispatchers.Unconfined)
    val progress = SqlReadingProgressRepository(database, Dispatchers.Unconfined)
    val layouts = SqlPdfLayoutRepository(database, Dispatchers.Unconfined)
    val readingSessions = SqlReadingSessionRepository(database, Dispatchers.Unconfined)
    val store = BookStateStore(database, Dispatchers.Unconfined)
    val sessionStore = SessionStore(database, Dispatchers.Unconfined)
    private val cursors = SyncCursors(settings)
    private val http = ServerHttp(serverHttpClient())

    private val pull = PullServerLibraryUseCase(
        session,
        ServerLibraryApi(http),
        SqlServerShelf(database, Dispatchers.Unconfined),
        JvmBookFileStore(downloads),
        cursors,
        ShelfChanges(),
    )
    private val syncState = SyncBookStateUseCase(session, BookStateApi(http), store, cursors, ShelfChanges())
    val syncSessions = SyncSessionsUseCase(session, SessionApi(http), sessionStore, HistoryChoice(settings), cursors)

    /** Server books arrive hashed, and the tests import no others, so nothing here needs reading. */
    private val hashLibrary = HashLibraryUseCase(SqlContentHashStore(database, Dispatchers.Unconfined)) { null }
    private val round = SyncRoundUseCase(hashLibrary, pull, syncState, syncSessions, cursors)

    suspend fun signIn(setUp: Boolean) {
        if (setUp) {
            session.setUp(server.address, "reader", "correct horse battery")
        } else {
            session.signIn(server.address, "reader", "correct horse battery")
        }
        // What the coordinator does the moment the status turns to signed in.
        syncSessions.signedIn()
    }

    /** Signs out, leaving the shelf as it was - what a reader who never signed in would have. */
    suspend fun forgetSignIn() = session.signOut()

    suspend fun pullLibrary() {
        pull()
    }

    /** Everything, as the coordinator runs it. */
    suspend fun round() = round.invoke()

    suspend fun sync() {
        syncState()
        syncSessions()
    }

    suspend fun locationOfBook(): String = library.getDocuments().single().document.location

    /** Moves the last change to [part] to a chosen time, standing in for when it was made. */
    suspend fun dateLastChange(part: SyncedPart, at: Long) {
        database.syncOutboxQueries.record(part.stored, at, locationOfBook())
    }

    override fun close() {
        downloads.deleteRecursively()
    }
}
