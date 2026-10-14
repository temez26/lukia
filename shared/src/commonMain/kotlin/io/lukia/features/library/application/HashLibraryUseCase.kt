package io.lukia.features.library.application

import io.lukia.features.library.domain.ContentHashStore
import io.lukia.features.library.domain.ContentHasher

/**
 * Hashes every book on the shelf not hashed yet, so it can be matched to the
 * same book on a sync server and on other devices.
 *
 * Run once somebody signs in, and again after imports while they stay signed
 * in. A book that cannot be read is passed over and tried again next time; one
 * hashed is never hashed again unless its file changes size (see `Document.sq`).
 *
 * Book by book, each recorded as soon as it is known, so a pass cut short by the
 * app closing loses one book's work rather than the library's.
 */
class HashLibraryUseCase(
    private val store: ContentHashStore,
    private val hasher: ContentHasher,
) {
    /** How many books were hashed. */
    suspend operator fun invoke(): Int {
        var hashed = 0

        for (document in store.unhashed()) {
            val hash = hasher.contentHash(document) ?: continue
            store.record(document.location, hash)
            hashed++
        }

        return hashed
    }
}
