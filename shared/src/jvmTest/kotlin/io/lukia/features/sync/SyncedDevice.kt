package io.lukia.features.sync

import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.lukia.api.ApiRoutes
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
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
import io.lukia.features.library.infrastructure.importing.DesktopDocumentAnalyzer
import io.lukia.features.reader.infrastructure.SqlPdfLayoutRepository
import io.lukia.features.reader.infrastructure.SqlReadingProgressRepository
import io.lukia.features.reader.infrastructure.SqlReadingSessionRepository
import io.lukia.features.sync.application.ForgetServerUseCase
import io.lukia.features.sync.application.PullServerLibraryUseCase
import io.lukia.features.sync.application.SyncBookStateUseCase
import io.lukia.features.sync.application.SyncCoordinator
import io.lukia.features.sync.application.SyncRoundUseCase
import io.lukia.features.sync.application.SyncSessionsUseCase
import io.lukia.features.sync.application.UploadNewBooksUseCase
import io.lukia.features.sync.application.UploadServerBookUseCase
import io.lukia.features.sync.infrastructure.BookStateApi
import io.lukia.features.sync.infrastructure.BookStateStore
import io.lukia.features.sync.infrastructure.HistoryChoice
import io.lukia.features.sync.infrastructure.ServerEvents
import io.lukia.features.sync.infrastructure.ServerLibraryApi
import io.lukia.features.sync.infrastructure.SessionApi
import io.lukia.features.sync.infrastructure.SessionStore
import io.lukia.features.sync.infrastructure.SyncCursors
import io.lukia.features.sync.infrastructure.UploadChoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

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

    private val shelf = SqlServerShelf(database, Dispatchers.Unconfined)
    private val analyzer = DesktopDocumentAnalyzer()
    val uploads = UploadChoice(settings)

    private val pull = PullServerLibraryUseCase(
        session,
        ServerLibraryApi(http),
        shelf,
        JvmBookFileStore(downloads),
        cursors,
        ShelfChanges(),
    )

    /** What this device's book-state sync tells the shelf when something here changed. */
    val stateChanges = ShelfChanges()
    private val syncState = SyncBookStateUseCase(session, BookStateApi(http), store, cursors, stateChanges)
    val syncSessions = SyncSessionsUseCase(session, SessionApi(http), sessionStore, HistoryChoice(settings), cursors)

    private val hashLibrary = HashLibraryUseCase(SqlContentHashStore(database, Dispatchers.Unconfined), analyzer)
    private val uploadNewBooks = UploadNewBooksUseCase(
        shelf,
        UploadServerBookUseCase(session, ServerLibraryApi(http), library, analyzer, shelf, ShelfChanges()),
        cursors,
        uploads,
    )
    private val round = SyncRoundUseCase(hashLibrary, pull, syncState, syncSessions, cursors, uploadNewBooks)

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

    /**
     * This device's coordinator, as the app wires it up, with nothing waiting to
     * be sent unless [waiting] says otherwise. [lineRefused] stands in for a server whose line will not open -
     * a proxy that will not hold one - while every other request goes through.
     */
    fun coordinator(
        checkEvery: Duration = 2.minutes,
        lineRefused: Boolean = false,
        waiting: Flow<Long> = emptyFlow(),
        sendAfter: Duration = 5.seconds,
    ) = SyncCoordinator(
        session = session,
        round = round,
        syncSessions = syncSessions,
        forgetServer = ForgetServerUseCase(shelf, cursors, ShelfChanges()),
        waiting = waiting,
        events = ServerEvents(if (lineRefused) ServerHttp(lineRefusingClient()) else http),
        checkEvery = checkEvery,
        sendAfter = sendAfter,
    )

    private fun lineRefusingClient() = serverHttpClient().apply {
        plugin(HttpSend).intercept { request ->
            if (request.url.buildString().endsWith(ApiRoutes.MyEvents)) {
                throw IOException("The line will not open")
            }
            execute(request)
        }
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

    /** Puts a book on this device's shelf the way an import does: a real file, added to the library. */
    suspend fun importBook(name: String, bytes: ByteArray) {
        val file = File(downloads, name).apply { writeBytes(bytes) }
        library.addDocument(
            DocumentInfo(
                document = PickedDocument(name, name.substringAfterLast('.'), file.path),
                type = DocumentType.EPUB,
                exists = true,
                sizeBytes = file.length(),
            ),
        )
    }

    override fun close() {
        downloads.deleteRecursively()
    }
}
