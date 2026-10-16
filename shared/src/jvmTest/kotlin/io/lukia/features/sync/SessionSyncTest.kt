package io.lukia.features.sync

import io.lukia.core.infrastructure.epub.MinimalEpub
import io.lukia.features.account.TestSyncServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The time spent reading, counted on every device the account reads on - and
 * the reading done before signing in going to the account only if the reader
 * says so.
 */
class SessionSyncTest {

    private val server = TestSyncServer()
    private val phone = SyncedDevice(server)
    private val laptop = SyncedDevice(server)

    @AfterTest
    fun cleanUp() {
        server.close()
        phone.close()
        laptop.close()
    }

    @Test
    fun aSittingOnOneDeviceIsCountedOnTheOther() = runBlocking {
        bothOnTheSameBook()

        val sitting = phone.readingSessions.start(phone.locationOfBook(), startedAt = 1_000)
        phone.readingSessions.extend(sitting, durationMs = 600_000)
        phone.sync()
        laptop.sync()

        assertEquals(listOf(600_000L), laptop.readingSessions.all().map { it.durationMs })
    }

    @Test
    fun aSittingThatGrowsIsCountedOnceAtItsFullLength() = runBlocking {
        bothOnTheSameBook()
        val sitting = phone.readingSessions.start(phone.locationOfBook(), startedAt = 1_000)

        phone.readingSessions.extend(sitting, durationMs = 60_000)
        phone.sync()
        laptop.sync()
        phone.readingSessions.extend(sitting, durationMs = 900_000)
        phone.sync()
        laptop.sync()

        assertEquals(listOf(900_000L), laptop.readingSessions.all().map { it.durationMs })
    }

    @Test
    fun aSittingFromAnotherDeviceIsNeverSentBack() = runBlocking {
        bothOnTheSameBook()
        val sitting = phone.readingSessions.start(phone.locationOfBook(), startedAt = 1_000)
        phone.readingSessions.extend(sitting, durationMs = 60_000)
        phone.sync()

        laptop.sync()

        assertTrue(laptop.sessionStore.unsent().isEmpty())
    }

    @Test
    fun readingFromBeforeSigningInWaitsForTheReadersAnswer() = runBlocking {
        server.setUpWithBook()
        laptop.pullLibraryAfterReading(minutes = 30)

        laptop.sync()

        assertTrue(laptop.syncSessions.historyQuestion.value)
        phone.signIn(setUp = false)
        phone.sync()
        assertTrue(phone.readingSessions.all().isEmpty(), "nothing is sent before the answer")
    }

    /**
     * What is waiting to go is what a round would send: reading held back for
     * the answer is not, or the count never reaches zero and every round that
     * sends is followed by one with nothing to send.
     */
    @Test
    fun readingHeldBackForTheAnswerIsNotWaitingToGo() = runBlocking {
        server.setUpWithBook()
        laptop.pullLibraryAfterReading(minutes = 30)
        laptop.sync()
        assertEquals(0, laptop.syncSessions.waiting.first(), "held back, so not waiting")

        val later = laptop.readingSessions.start(laptop.locationOfBook(), startedAt = 9_000)
        laptop.readingSessions.extend(later, durationMs = 120_000)
        assertEquals(1, laptop.syncSessions.waiting.first())

        laptop.sync()
        assertEquals(0, laptop.syncSessions.waiting.first(), "sent, and the held-back reading still waits")

        laptop.syncSessions.answer(addToAccount = true)
        assertEquals(1, laptop.syncSessions.waiting.first(), "added to the account, so it goes")
    }

    @Test
    fun keptOnTheDeviceItStaysThereWhileNewReadingGoesUp() = runBlocking {
        server.setUpWithBook()
        laptop.pullLibraryAfterReading(minutes = 30)
        laptop.sync()

        laptop.syncSessions.answer(addToAccount = false)
        val later = laptop.readingSessions.start(laptop.locationOfBook(), startedAt = 9_000)
        laptop.readingSessions.extend(later, durationMs = 120_000)
        laptop.sync()
        phone.signIn(setUp = false)
        phone.sync()

        assertFalse(laptop.syncSessions.historyQuestion.value)
        assertEquals(listOf(120_000L), phone.readingSessions.all().map { it.durationMs })
        assertEquals(2, laptop.readingSessions.all().size, "the kept sitting is still counted here")
    }

    @Test
    fun addedToTheAccountItIsCountedEverywhere() = runBlocking {
        server.setUpWithBook()
        laptop.pullLibraryAfterReading(minutes = 30)
        laptop.sync()

        laptop.syncSessions.answer(addToAccount = true)
        laptop.sync()
        phone.signIn(setUp = false)
        phone.sync()

        assertEquals(listOf(30 * 60_000L), phone.readingSessions.all().map { it.durationMs })
    }

    private suspend fun bothOnTheSameBook() {
        phone.signIn(setUp = true)
        phone.session.authorized { _, token -> server.upload(token, "moby-dick.epub", MinimalEpub.book()) }
        laptop.signIn(setUp = false)
        phone.pullLibrary()
        laptop.pullLibrary()
    }

    /** The server set up, by a device that then leaves, with one book in its library. */
    private suspend fun TestSyncServer.setUpWithBook() {
        val admin = SyncedDevice(this)
        try {
            admin.signIn(setUp = true)
            admin.session.authorized { _, token -> upload(token, "moby-dick.epub", MinimalEpub.book()) }
        } finally {
            admin.close()
        }
    }

    /** Reads the book for [minutes] before ever signing in, then signs in. */
    private suspend fun SyncedDevice.pullLibraryAfterReading(minutes: Long) {
        // The book arrives on the shelf through a first, short-lived sign-in,
        // which is then undone as though it never happened.
        signIn(setUp = false)
        pullLibrary()
        val sitting = readingSessions.start(locationOfBook(), startedAt = 1_000)
        readingSessions.extend(sitting, durationMs = minutes * 60_000)
        forgetSignIn()
        signIn(setUp = false)
    }
}
