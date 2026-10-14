package io.lukia.features.library.infrastructure

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.infrastructure.testDatabase
import io.lukia.features.reader.infrastructure.SqlReadingSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SqlContentHashStoreTest {

    private val database = testDatabase()
    private val library = SqlLibraryRepository(database, Dispatchers.Unconfined)
    private val store = SqlContentHashStore(database, Dispatchers.Unconfined)
    private val sessions = SqlReadingSessionRepository(database, Dispatchers.Unconfined)

    @Test
    fun aBookIsUnhashedUntilItsHashIsRecorded() = runTest {
        library.addDocument(book("moby.pdf"))
        library.addDocument(book("ahab.pdf"))

        store.record(location("moby.pdf"), MobyHash)

        assertEquals(listOf(PickedDocument("ahab.pdf", "pdf", location("ahab.pdf"))), store.unhashed())
    }

    @Test
    fun aBookWhoseFileIsGoneIsNotHashed() = runTest {
        library.addDocument(book("moby.pdf").copy(exists = false))

        assertTrue(store.unhashed().isEmpty())
    }

    @Test
    fun aSessionStartedBeforeTheHashWasKnownLearnsIt() = runTest {
        library.addDocument(book("moby.pdf"))
        sessions.start(location("moby.pdf"), startedAt = 1_000)

        store.record(location("moby.pdf"), MobyHash)

        assertEquals(
            listOf(MobyHash),
            database.readingSessionQueries.selectAll().executeAsList().map {
                it.contentHash
            },
        )
    }

    @Test
    fun aSessionStartedAfterwardsCarriesTheHashAndTitleFromTheStart() = runTest {
        library.addDocument(book("moby.pdf"))
        store.record(location("moby.pdf"), MobyHash)

        sessions.start(location("moby.pdf"), startedAt = 1_000)
        sessions.start(location("moby.pdf"), startedAt = 2_000)

        val rows = database.readingSessionQueries.selectAll().executeAsList()
        assertEquals(listOf(MobyHash, MobyHash), rows.map { it.contentHash })
        assertEquals(listOf("moby.pdf", "moby.pdf"), rows.map { it.title })
        rows.forEach { assertNotNull(it.uuid) }
        assertNotEquals(rows[0].uuid, rows[1].uuid)
    }

    @Test
    fun reImportingTheSameFileKeepsTheHash() = runTest {
        library.addDocument(book("moby.pdf"))
        store.record(location("moby.pdf"), MobyHash)

        library.addDocument(book("moby.pdf"))

        assertTrue(store.unhashed().isEmpty())
    }

    @Test
    fun aFileThatChangedSizeIsHashedAgain() = runTest {
        library.addDocument(book("moby.pdf"))
        store.record(location("moby.pdf"), MobyHash)

        library.addDocument(book("moby.pdf").copy(sizeBytes = 2048))

        assertEquals(listOf(location("moby.pdf")), store.unhashed().map { it.location })
    }

    private fun location(name: String) = "file:///books/$name"

    private fun book(name: String) = DocumentInfo(
        document = PickedDocument(name, name.substringAfterLast('.'), location(name)),
        type = DocumentType.PDF,
        exists = true,
        sizeBytes = 1024,
        coverImage = null,
        metadata = null,
    )

    private companion object {
        const val MobyHash = "3de332f731c6b7fa39018a1f12d5e9989333ad0d1d3d4f7174333e4e8ec21768"
    }
}
