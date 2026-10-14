package io.lukia.features.library

import io.lukia.core.domain.document.PickedDocument
import io.lukia.features.library.application.HashLibraryUseCase
import io.lukia.features.library.domain.ContentHashStore
import io.lukia.features.library.domain.ContentHasher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HashLibraryUseCaseTest {

    private val moby = PickedDocument("moby.pdf", "pdf", "file:///moby.pdf")
    private val ahab = PickedDocument("ahab.epub", "epub", "file:///ahab.epub")

    @Test
    fun everyUnhashedBookIsHashedAndRecorded() = runTest {
        val store = FakeContentHashStore(listOf(moby, ahab))

        val hashed = HashLibraryUseCase(store) { "hash of ${it.fileName}" }()

        assertEquals(2, hashed)
        assertEquals(
            mapOf(moby.location to "hash of moby.pdf", ahab.location to "hash of ahab.epub"),
            store.recorded,
        )
    }

    @Test
    fun aBookThatCannotBeReadIsLeftForNextTime() = runTest {
        val store = FakeContentHashStore(listOf(moby, ahab))
        val hasher = ContentHasher { if (it == moby) null else "hash" }

        val hashed = HashLibraryUseCase(store, hasher)()

        assertEquals(1, hashed)
        assertEquals(mapOf(ahab.location to "hash"), store.recorded)
    }

    private class FakeContentHashStore(private val unhashed: List<PickedDocument>) : ContentHashStore {
        val recorded = mutableMapOf<String, String>()

        override suspend fun unhashed() = unhashed.filterNot { it.location in recorded }

        override suspend fun record(location: String, contentHash: String) {
            recorded[location] = contentHash
        }
    }
}
