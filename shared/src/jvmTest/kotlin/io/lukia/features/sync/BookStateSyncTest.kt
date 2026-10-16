package io.lukia.features.sync

import io.lukia.core.domain.reading.PdfLayout
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.infrastructure.SyncedPart
import io.lukia.core.infrastructure.epub.MinimalEpub
import io.lukia.features.account.TestSyncServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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

    private val phone = SyncedDevice(server)
    private val laptop = SyncedDevice(server)

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
        val tablet = SyncedDevice(server)
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
    fun aPlaceArrivingBeforeItsBookIsKeptForWhenTheBookComes() = runBlocking {
        phone.signIn(setUp = true)
        phone.session.authorized { _, token -> server.upload(token, "moby-dick.epub", epub) }
        phone.pullLibrary()
        phone.progress.save(phone.locationOfBook(), ReadingProgress(pageIndex = 41, pageCount = 300))
        phone.sync()

        // The laptop hears of the place before it has the book to put it in.
        laptop.signIn(setUp = false)
        laptop.sync()
        laptop.round()

        assertEquals(41, laptop.progress.progressFor(laptop.locationOfBook())?.pageIndex)
    }

    @Test
    fun nothingIsLeftWaitingOnceTheServerHasIt() = runBlocking {
        bothOnTheSameBook()

        phone.progress.save(phone.locationOfBook(), ReadingProgress(pageIndex = 5, pageCount = 300))
        phone.library.setFavorite(phone.locationOfBook(), true)
        phone.sync()

        assertTrue(phone.store.pending().isEmpty())
    }

    /**
     * A device sends its place, and the same round reads it back. That is what it
     * already has: nothing is written again and the shelf is not told of a
     * change, where before it reloaded the whole library after every page sent.
     */
    @Test
    fun aDevicesOwnPlaceComingBackIsNotAChange() = runBlocking {
        bothOnTheSameBook()
        phone.sync()
        laptop.sync()
        val phoneHeard = phone.stateChanges.changes.counting(this)
        val laptopHeard = laptop.stateChanges.changes.counting(this)

        phone.progress.save(phone.locationOfBook(), ReadingProgress(pageIndex = 41, pageCount = 300))
        phone.sync()
        laptop.sync()
        yield()

        assertEquals(0, phoneHeard.value, "the phone was told of its own page")
        assertEquals(1, laptopHeard.value, "the laptop was not told of the phone's page")
        coroutineContext.cancelChildren()
    }

    /** Counts what this flow emits from now on, in [scope]. */
    private fun Flow<Unit>.counting(scope: CoroutineScope): StateFlow<Int> {
        val count = MutableStateFlow(0)
        scope.launch(Dispatchers.Unconfined, start = CoroutineStart.UNDISPATCHED) { collect { count.value++ } }
        return count
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
}
