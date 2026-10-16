package io.lukia.features.sync

import io.lukia.core.infrastructure.epub.MinimalEpub
import io.lukia.features.account.TestSyncServer
import io.lukia.features.account.infrastructure.ServerHttp
import io.lukia.features.account.infrastructure.serverHttpClient
import io.lukia.features.sync.infrastructure.ServerEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

/** The server telling a listening device that something changed, against a real server and the app's real client. */
class LiveChangesTest {

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
    fun aListeningDeviceHearsOfANewBookAndOfAChangeOnAnotherDevice() = runBlocking {
        phone.signIn(setUp = true)
        laptop.signIn(setUp = false)
        val heard = Channel<String>(Channel.UNLIMITED)
        val events = ServerEvents(ServerHttp(serverHttpClient()))
        val listening = launch(Dispatchers.IO) {
            laptop.session.authorized { address, token -> events.listen(address, token) { heard.send(it) } }
        }
        // Long enough for the line to open; anything said before then is not heard.
        delay(OpenWithin)

        phone.session.authorized { _, token -> server.upload(token, "moby-dick.epub", MinimalEpub.book()) }
        assertEquals("library", withTimeout(HeardWithin) { heard.receive() })

        phone.pullLibrary()
        phone.library.setFavorite(phone.locationOfBook(), true)
        phone.sync()
        assertEquals("state", withTimeout(HeardWithin) { heard.receive() })

        listening.cancel()
    }

    /** The device that turned the page already has it; only the others are told. */
    @Test
    fun aDeviceIsNotToldOfItsOwnChange() = runBlocking {
        phone.signIn(setUp = true)
        laptop.signIn(setUp = false)
        val events = ServerEvents(ServerHttp(serverHttpClient()))
        val phoneHeard = Channel<String>(Channel.UNLIMITED)
        val laptopHeard = Channel<String>(Channel.UNLIMITED)
        val listening = listOf(phone to phoneHeard, laptop to laptopHeard).map { (device, heard) ->
            launch(Dispatchers.IO) {
                device.session.authorized { address, token -> events.listen(address, token) { heard.send(it) } }
            }
        }
        delay(OpenWithin)

        // The upload is the phone's own as well: only the laptop hears of it.
        phone.session.authorized { _, token -> server.upload(token, "moby-dick.epub", MinimalEpub.book()) }
        assertEquals("library", withTimeout(HeardWithin) { laptopHeard.receive() })
        phone.pullLibrary()

        phone.library.setFavorite(phone.locationOfBook(), true)
        phone.sync()

        assertEquals("state", withTimeout(HeardWithin) { laptopHeard.receive() })
        assertNull(withTimeoutOrNull(QuietFor) { phoneHeard.receive() }, "the phone was told of its own change")

        listening.forEach { it.cancel() }
    }

    private companion object {
        val OpenWithin = 1.seconds
        val HeardWithin = 5.seconds

        /** Long enough that a hint on its way would have arrived. */
        val QuietFor = 2.seconds
    }
}
