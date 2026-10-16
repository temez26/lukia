package io.lukia.features.sync

import io.lukia.core.infrastructure.epub.MinimalEpub
import io.lukia.features.account.TestSyncServer
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A book imported on one device reaching every other without anybody sending it. */
class UploadNewBooksTest {

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
    fun aBookImportedAfterSigningInGoesToTheServerOnItsOwn() = runBlocking {
        phone.signIn(setUp = true)
        phone.round()

        phone.importBook("moby-dick.epub", MinimalEpub.book())
        phone.round()

        laptop.signIn(setUp = false)
        laptop.pullLibrary()
        val arrived = laptop.library.getDocuments().single()
        assertEquals("moby-dick.epub", arrived.document.fileName)
        assertTrue(arrived.onServer)
        assertTrue(phone.library.getDocuments().single().onServer)
    }

    @Test
    fun theLibraryTheDeviceCameWithStaysOnIt() = runBlocking {
        phone.importBook("moby-dick.epub", MinimalEpub.book())
        phone.signIn(setUp = true)

        phone.round()
        phone.round()

        laptop.signIn(setUp = false)
        laptop.pullLibrary()
        assertTrue(laptop.library.getDocuments().isEmpty())
    }

    @Test
    fun turnedOffNothingIsSent() = runBlocking {
        phone.uploads.enabled = false
        phone.signIn(setUp = true)
        phone.round()

        phone.importBook("moby-dick.epub", MinimalEpub.book())
        phone.round()

        laptop.signIn(setUp = false)
        laptop.pullLibrary()
        assertTrue(laptop.library.getDocuments().isEmpty())
    }
}
