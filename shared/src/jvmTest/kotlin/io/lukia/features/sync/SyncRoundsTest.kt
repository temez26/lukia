package io.lukia.features.sync

import io.lukia.api.ApiRoutes
import io.lukia.features.account.TestSyncServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** How many rounds the coordinator runs for what asks it to, counted at a real server. */
class SyncRoundsTest {

    private val server = TestSyncServer()
    private val phone = SyncedDevice(server)

    @AfterTest
    fun cleanUp() {
        server.close()
        phone.close()
    }

    /** One round each asks for the account's book states once. */
    private fun rounds() = server.requestsTo(ApiRoutes.MyBooks)

    @Test
    fun aLaunchWhileSignedInCatchesUpOnce() = runBlocking {
        phone.signIn(setUp = true)
        val sync = phone.coordinator()

        val running = launch(Dispatchers.IO) { sync.run() }
        // What the app does as it comes to the front.
        sync.syncNow()
        delay(Settle)

        assertEquals(1, rounds())
        running.cancel()
    }

    @Test
    fun signingInWhileTheAppIsOpenCatchesUp() = runBlocking {
        val sync = phone.coordinator()
        val running = launch(Dispatchers.IO) { sync.run() }
        delay(Settle)

        phone.signIn(setUp = true)
        withTimeout(Settle * 4) { sync.status.first { it.lastSyncedAt != null } }

        assertEquals(1, rounds())
        running.cancel()
    }

    @Test
    fun requestsDuringARoundCostOneMoreRoundNotOneEach() = runBlocking {
        phone.signIn(setUp = true)
        val sync = phone.coordinator()

        val first = launch(Dispatchers.IO) { sync.syncNow() }
        sync.status.first { it.running }
        val asked = List(3) { launch(Dispatchers.IO) { sync.syncNow() } }
        first.join()
        asked.forEach { it.join() }

        assertEquals(2, rounds())
    }

    @Test
    fun anOpenLineCatchesUpOnceAndIsNotPolled() = runBlocking {
        phone.signIn(setUp = true)
        val sync = phone.coordinator(checkEvery = 100.milliseconds)

        val keepingUp = launch(Dispatchers.IO) { sync.keepUp() }
        // Past the three seconds a line has to open, with a check due every tenth
        // of one had the line not been trusted.
        delay(5.seconds)

        assertEquals(1, rounds())
        keepingUp.cancel()
    }

    @Test
    fun aLineThatWillNotOpenIsMadeUpForByChecking() = runBlocking {
        phone.signIn(setUp = true)
        val sync = phone.coordinator(checkEvery = 500.milliseconds, lineRefused = true)

        val keepingUp = launch(Dispatchers.IO) { sync.keepUp() }
        // Three seconds for the line to open, then a check every half second.
        delay(5.seconds)

        assertTrue(rounds() >= 3, "only ${rounds()} rounds without a line")
        keepingUp.cancel()
    }

    /**
     * A round sends what is waiting, and sending it brings the count back to
     * zero - which is a change to the count, but nothing to send.
     */
    @Test
    fun onlySomethingWaitingStartsARound() = runBlocking {
        phone.signIn(setUp = true)
        val waiting = MutableSharedFlow<Long>(extraBufferCapacity = 1)
        val sync = phone.coordinator(waiting = waiting, sendAfter = 100.milliseconds)
        val running = launch(Dispatchers.IO) { sync.run() }
        delay(Settle)

        waiting.emit(1)
        delay(Settle)
        assertEquals(1, rounds(), "something waiting was not sent")

        waiting.emit(0)
        delay(Settle)
        assertEquals(1, rounds(), "nothing waiting started a round")

        // A count that is back at zero before it settles - something waiting
        // for a moment, then sent or received - is nothing waiting either.
        waiting.emit(1)
        waiting.emit(0)
        delay(Settle)
        assertEquals(1, rounds(), "a count that settled at zero started a round")

        running.cancel()
    }

    private companion object {
        /** Long enough for a round against a local server to have finished. */
        val Settle = 1.seconds
    }
}
