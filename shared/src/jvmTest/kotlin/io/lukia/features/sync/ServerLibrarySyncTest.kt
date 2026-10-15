package io.lukia.features.sync

import io.lukia.core.domain.AppErrorException
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.document.ShelfChanges
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.core.infrastructure.JvmBookFileStore
import io.lukia.core.infrastructure.epub.MinimalEpub
import io.lukia.core.infrastructure.testDatabase
import io.lukia.features.account.InMemoryAccountStore
import io.lukia.features.account.TestSyncServer
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.infrastructure.KtorServerApi
import io.lukia.features.account.infrastructure.ServerHttp
import io.lukia.features.account.infrastructure.serverHttpClient
import io.lukia.features.library.infrastructure.SqlContentHashStore
import io.lukia.features.library.infrastructure.SqlLibraryRepository
import io.lukia.features.library.infrastructure.SqlServerShelf
import io.lukia.features.sync.application.DownloadServerBookUseCase
import io.lukia.features.sync.application.PullServerLibraryUseCase
import io.lukia.features.sync.infrastructure.ServerLibraryApi
import io.lukia.features.sync.infrastructure.SyncCursors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shared library reaching a device: the real server, the app's real client,
 * a real database and a real directory to download into.
 */
class ServerLibrarySyncTest {

    private val server = TestSyncServer()
    private val session = AccountSession(KtorServerApi(serverHttpClient()), InMemoryAccountStore())
    private val database = testDatabase()
    private val library = SqlLibraryRepository(database, Dispatchers.Unconfined)
    private val shelf = SqlServerShelf(database, Dispatchers.Unconfined)
    private val downloads = Files.createTempDirectory("lukia-downloads").toFile()
    private val files = JvmBookFileStore(downloads)
    private val api = ServerLibraryApi(ServerHttp(serverHttpClient()))
    private val cursors = SyncCursors(FakeSettingsStore())
    private val changes = ShelfChanges()
    private val pull = PullServerLibraryUseCase(session, api, shelf, files, cursors, changes)
    private val download = DownloadServerBookUseCase(session, api, shelf, files, changes)

    private val epub = MinimalEpub.book()
    private val epubHash = sha256(epub)

    @AfterTest
    fun cleanUp() {
        server.close()
        downloads.deleteRecursively()
    }

    @Test
    fun aServerBookArrivesOnTheShelfReadyToDownload() = runBlocking {
        signInAndUpload("moby-dick.epub" to epub)

        pull()

        val book = library.getDocuments().single()
        assertEquals("moby-dick.epub", book.document.fileName)
        assertEquals(DocumentType.EPUB, book.type)
        assertEquals(MinimalEpub.TITLE, book.metadata?.title)
        assertTrue(book.onServer)
        assertFalse(book.exists)
        assertEquals(files.locationFor(epubHash, "epub"), book.document.location)
    }

    @Test
    fun aDownloadedBookIsTheServersBytesWhereTheShelfExpectsThem() = runBlocking {
        signInAndUpload("moby-dick.epub" to epub)
        pull()
        val location = library.getDocuments().single().document.location
        val progress = mutableListOf<Float>()

        download.download(location) { progress += it }

        assertContentEquals(epub, File(location).readBytes())
        assertTrue(library.getDocuments().single().exists)
        assertEquals(1f, progress.last())
        assertTrue(downloads.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test
    fun aBookAlreadyOnTheDeviceIsRecognisedRatherThanOfferedTwice() = runBlocking {
        val local = File(downloads, "my-copy.epub").apply { writeBytes(epub) }
        library.addDocument(
            DocumentInfo(
                PickedDocument("my-copy.epub", "epub", local.path),
                DocumentType.EPUB,
                true,
                epub.size.toLong(),
            ),
        )
        SqlContentHashStore(database, Dispatchers.Unconfined).record(local.path, epubHash)
        signInAndUpload("moby-dick.epub" to epub)

        pull()

        val only = library.getDocuments().single()
        assertEquals(local.path, only.document.location)
        assertTrue(only.onServer)
    }

    @Test
    fun aBookRemovedFromTheServerLeavesTheShelfUnlessItWasDownloaded() = runBlocking {
        val admin = signInAndUpload("moby-dick.epub" to epub, "notes.txt" to "Call me Ishmael.".toByteArray())
        pull()
        val notes = library.getDocuments().first { it.document.fileName == "notes.txt" }.document.location
        download.download(notes) {}

        removeOnServer(admin, epubHash)
        removeOnServer(admin, sha256("Call me Ishmael.".toByteArray()))
        pull()

        val left = library.getDocuments().single()
        assertEquals("notes.txt", left.document.fileName)
        assertTrue(left.exists)
        assertFalse(left.onServer)
    }

    @Test
    fun aCatchUpAsksOnlyForWhatChangedSinceTheLast() = runBlocking {
        signInAndUpload("moby-dick.epub" to epub)
        pull()
        val after = cursors.books()

        pull()

        assertEquals(after, cursors.books())
        assertEquals(1, library.getDocuments().size)
    }

    @Test
    fun signingOutTakesAwayWhatWasNeverDownloaded() = runBlocking {
        signInAndUpload("moby-dick.epub" to epub, "notes.txt" to "Call me Ishmael.".toByteArray())
        pull()
        val notes = library.getDocuments().first { it.document.fileName == "notes.txt" }.document.location
        download.download(notes) {}

        shelf.forgetServer()

        val left = library.getDocuments().single()
        assertEquals("notes.txt", left.document.fileName)
        assertFalse(left.onServer)
    }

    @Test
    fun aFailedDownloadLeavesNothingBehind() = runBlocking {
        signInAndUpload("moby-dick.epub" to epub)
        pull()
        val location = library.getDocuments().single().document.location
        server.stop()

        runCatching { download.download(location) {} }
            .onSuccess { error("a download from a stopped server should fail") }
            .onFailure { assertTrue(it is AppErrorException) }

        assertFalse(File(location).exists())
        assertTrue(downloads.listFiles().orEmpty().isEmpty())
        assertFalse(library.getDocuments().single().exists)
    }

    /** Sets the server up as `admin`, signs this device in, and uploads [books]. Answers the admin's token. */
    private suspend fun signInAndUpload(vararg books: Pair<String, ByteArray>): String {
        session.setUp(server.address, "admin", "correct horse battery")
        return session.authorized { _, token ->
            books.forEach { (name, bytes) -> server.upload(token, name, bytes) }
            token
        }
    }

    private suspend fun removeOnServer(token: String, id: String) {
        server.remove(token, id)
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
