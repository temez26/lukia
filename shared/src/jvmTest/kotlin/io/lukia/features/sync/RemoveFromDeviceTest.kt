package io.lukia.features.sync

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.document.ShelfChanges
import io.lukia.core.domain.reading.FakePdfLayoutRepository
import io.lukia.core.domain.reading.FakeReadingProgressRepository
import io.lukia.core.domain.storage.FakeDocumentWorkspace
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.core.infrastructure.JvmBookFileStore
import io.lukia.core.infrastructure.JvmIncomingDocumentStore
import io.lukia.core.infrastructure.epub.MinimalEpub
import io.lukia.core.infrastructure.testDatabase
import io.lukia.features.account.InMemoryAccountStore
import io.lukia.features.account.TestSyncServer
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.infrastructure.KtorServerApi
import io.lukia.features.account.infrastructure.ServerHttp
import io.lukia.features.account.infrastructure.serverHttpClient
import io.lukia.features.library.application.RemoveDocumentUseCase
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Taking a book off this device when the server has it too: the server keeps
 * it, and so does the shelf, as a book to download again. A catch-up only sends
 * what changed, so a row dropped here would never be offered again.
 */
class RemoveFromDeviceTest {

    private val server = TestSyncServer()
    private val session = AccountSession(KtorServerApi(serverHttpClient()), InMemoryAccountStore())
    private val database = testDatabase()
    private val library = SqlLibraryRepository(database, Dispatchers.Unconfined)
    private val shelf = SqlServerShelf(database, Dispatchers.Unconfined)
    private val downloads = Files.createTempDirectory("lukia-downloads").toFile()
    private val elsewhere = Files.createTempDirectory("lukia-reader").toFile()
    private val files = JvmBookFileStore(downloads)
    private val api = ServerLibraryApi(ServerHttp(serverHttpClient()))
    private val changes = ShelfChanges()
    private val pull = PullServerLibraryUseCase(session, api, shelf, files, SyncCursors(FakeSettingsStore()), changes)
    private val download = DownloadServerBookUseCase(session, api, shelf, files, changes)
    private val remove = RemoveDocumentUseCase(
        library,
        FakeDocumentWorkspace(),
        JvmIncomingDocumentStore(downloads),
        FakeReadingProgressRepository(),
        FakePdfLayoutRepository(),
        shelf,
        files,
    )

    private val epub = MinimalEpub.book()
    private val epubHash = sha256(epub)

    @AfterTest
    fun cleanUp() {
        server.close()
        downloads.deleteRecursively()
        elsewhere.deleteRecursively()
    }

    @Test
    fun aDownloadedBookRemovedFromTheDeviceWaitsToBeDownloadedAgain() = runBlocking {
        signInAndUpload()
        pull()
        val location = library.getDocuments().single().document.location
        download.download(location) {}

        remove(location)
        pull()

        val book = library.getDocuments().single()
        assertEquals(location, book.document.location)
        assertTrue(book.awaitsDownload)
        assertFalse(File(location).exists())
    }

    @Test
    fun anImportedCopyRemovedFromTheDeviceLeavesTheServersBookInItsPlace() = runBlocking {
        // Outside the app's own directory, where a reader's file would be.
        val local = File(elsewhere, "my-copy.epub").apply { writeBytes(epub) }
        library.addDocument(
            DocumentInfo(
                PickedDocument("my-copy.epub", "epub", local.path),
                DocumentType.EPUB,
                true,
                epub.size.toLong(),
            ),
        )
        SqlContentHashStore(database, Dispatchers.Unconfined).record(local.path, epubHash)
        signInAndUpload()
        pull()

        remove(local.path)

        val book = library.getDocuments().single()
        assertEquals(files.locationFor(epubHash, "epub"), book.document.location)
        assertTrue(book.awaitsDownload)
        assertTrue(local.exists(), "the reader's own file is not the app's to delete")
    }

    @Test
    fun aBookOnlyOnTheDeviceLeavesTheShelf() = runBlocking {
        val local = File(downloads, "notes.epub").apply { writeBytes(epub) }
        library.addDocument(
            DocumentInfo(PickedDocument("notes.epub", "epub", local.path), DocumentType.EPUB, true, epub.size.toLong()),
        )

        remove(local.path)

        assertTrue(library.getDocuments().isEmpty())
    }

    private suspend fun signInAndUpload() {
        session.setUp(server.address, "admin", "correct horse battery")
        session.authorized { _, token -> server.upload(token, "moby-dick.epub", epub) }
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
