package io.lukia.features.sync

import io.lukia.core.domain.document.ShelfChanges
import io.lukia.core.domain.reading.PdfLayout
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.core.infrastructure.JvmBookFileStore
import io.lukia.core.infrastructure.SyncedPart
import io.lukia.core.infrastructure.epub.MinimalEpub
import io.lukia.core.infrastructure.testDatabase
import io.lukia.features.account.InMemoryAccountStore
import io.lukia.features.account.TestSyncServer
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.infrastructure.KtorServerApi
import io.lukia.features.account.infrastructure.ServerHttp
import io.lukia.features.account.infrastructure.serverHttpClient
import io.lukia.features.library.infrastructure.SqlLibraryRepository
import io.lukia.features.library.infrastructure.SqlServerShelf
import io.lukia.features.reader.infrastructure.SqlPdfLayoutRepository
import io.lukia.features.reader.infrastructure.SqlReadingProgressRepository
import io.lukia.features.sync.application.PullServerLibraryUseCase
import io.lukia.features.sync.application.SyncBookStateUseCase
import io.lukia.features.sync.infrastructure.BookStateApi
import io.lukia.features.sync.infrastructure.BookStateStore
import io.lukia.features.sync.infrastructure.ServerLibraryApi
import io.lukia.features.sync.infrastructure.SyncCursors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * One account read on two devices against the real server: where the reader
 * is, how they read a PDF and what they favourited, carried from one to the
 * other, and the later change winning part by part.
 */
class BookStateSyncTest {

    private val server = TestSyncServer()
    private val epub = MinimalEpub.book()

    private val phone = Device()
    private val laptop = Device()

    @AfterTest
    fun cleanUp() {
        server.close()
        phone.close()
        laptop.close()
    }

    @Test
    fun aPlaceInABookFollowsTheReaderToTheirOtherDevice() = runBlocking {
        val location = bothOnTheSameBook()

        phone.progress.save(phone.locationOfBook(), ReadingProgress(pageIndex = 41, pageCount = 300))
        phone.sync()
        laptop.sync()

        assertEquals(41, laptop.progress.progressFor(location)?.pageIndex)
    }

    @Test
    fun theLaterChangeWinsWhicheverDeviceSyncsLast() = runBlocking {
        bothOnTheSameBook()

        phone.progress.save(phone.locationOfBook(), ReadingProgress(pageIndex = 10, pageCount = 300))
        laptop.progress.save(laptop.locationOfBook(), ReadingProgress(pageIndex = 20, pageCount = 300))
        // The laptop's page was turned later, but the phone is the one to sync last.
        phone.dateLastChange(SyncedPart.Progress, at = 1_000)
        laptop.dateLastChange(SyncedPart.Progress, at = 2_000)
        laptop.sync()
        phone.sync()
        laptop.sync()

        assertEquals(20, phone.progress.progressFor(phone.locationOfBook())?.pageIndex)
        assertEquals(20, laptop.progress.progressFor(laptop.locationOfBook())?.pageIndex)
    }

    @Test
    fun aFavouriteOnOneDeviceSurvivesAPageTurnOnTheOther() = runBlocking {
        bothOnTheSameBook()

        phone.library.setFavorite(phone.locationOfBook(), true)
        phone.dateLastChange(SyncedPart.Favorite, at = 1_000)
        laptop.progress.save(laptop.locationOfBook(), ReadingProgress(pageIndex = 7, pageCount = 300))
        laptop.dateLastChange(SyncedPart.Progress, at = 2_000)
        phone.sync()
        laptop.sync()
        phone.sync()

        for (device in listOf(phone, laptop)) {
            val book = device.library.getDocument(device.locationOfBook())
            assertEquals(true, book?.isFavorite)
            assertEquals(7, device.progress.progressFor(device.locationOfBook())?.pageIndex)
        }
    }

    @Test
    fun howAPdfIsReadTravelsToo() = runBlocking {
        bothOnTheSameBook()

        phone.layouts.save(phone.locationOfBook(), PdfLayout.Text)
        phone.sync()
        laptop.sync()

        assertEquals(PdfLayout.Text, laptop.layouts.layoutFor(laptop.locationOfBook()))
    }

    @Test
    fun whatWasDoneBeforeSigningInGoesToTheAccountWithoutOverrulingIt() = runBlocking {
        bothOnTheSameBook()
        laptop.progress.save(laptop.locationOfBook(), ReadingProgress(pageIndex = 99, pageCount = 300))
        laptop.sync()

        // A third device that read the book on its own before ever signing in.
        val tablet = Device()
        try {
            tablet.signIn(setUp = false)
            tablet.pullLibrary()
            tablet.database.syncOutboxQueries.clear()
            tablet.library.setFavorite(tablet.locationOfBook(), true)
            tablet.database.syncOutboxQueries.clear()
            tablet.database.readingPositionQueries.put(tablet.locationOfBook(), 3, 300)

            tablet.sync()
            laptop.sync()

            assertEquals(true, laptop.library.getDocument(laptop.locationOfBook())?.isFavorite)
            assertEquals(99, tablet.progress.progressFor(tablet.locationOfBook())?.pageIndex)
        } finally {
            tablet.close()
        }
    }

    @Test
    fun nothingIsLeftWaitingOnceTheServerHasIt() = runBlocking {
        bothOnTheSameBook()

        phone.progress.save(phone.locationOfBook(), ReadingProgress(pageIndex = 5, pageCount = 300))
        phone.library.setFavorite(phone.locationOfBook(), true)
        phone.sync()

        assertTrue(phone.store.pending().isEmpty())
    }

    /** Sets the server up with the book, and both devices signed in with it on their shelves. */
    private suspend fun bothOnTheSameBook(): String {
        phone.signIn(setUp = true)
        phone.session.authorized { _, token -> server.upload(token, "moby-dick.epub", epub) }
        laptop.signIn(setUp = false)
        phone.pullLibrary()
        laptop.pullLibrary()
        return laptop.locationOfBook()
    }

    private inner class Device : AutoCloseable {
        val database = testDatabase()
        private val downloads = Files.createTempDirectory("lukia-device").toFile()
        val session = AccountSession(KtorServerApi(serverHttpClient()), InMemoryAccountStore())
        val library = SqlLibraryRepository(database, Dispatchers.Unconfined)
        val progress = SqlReadingProgressRepository(database, Dispatchers.Unconfined)
        val layouts = SqlPdfLayoutRepository(database, Dispatchers.Unconfined)
        val store = BookStateStore(database, Dispatchers.Unconfined)
        private val cursors = SyncCursors(FakeSettingsStore())
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

        suspend fun signIn(setUp: Boolean) {
            if (setUp) {
                session.setUp(server.address, "reader", "correct horse battery")
            } else {
                session.signIn(server.address, "reader", "correct horse battery")
            }
        }

        suspend fun pullLibrary() = pull()

        suspend fun sync() = syncState()

        suspend fun locationOfBook(): String = library.getDocuments().single().document.location

        /** Moves the last change to [part] to a chosen time, standing in for when it was made. */
        suspend fun dateLastChange(part: SyncedPart, at: Long) {
            database.syncOutboxQueries.record(part.stored, at, locationOfBook())
        }

        override fun close() {
            downloads.deleteRecursively()
        }
    }
}
