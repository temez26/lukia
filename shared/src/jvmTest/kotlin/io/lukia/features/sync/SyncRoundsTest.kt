package io.lukia.features.sync

import io.lukia.api.ApiRoutes
import io.lukia.features.account.TestSyncServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private companion object {
        /** Long enough for a round against a local server to have finished. */
        val Settle = 1.seconds
    }
}
