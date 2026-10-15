package io.lukia.features.library.domain

import io.lukia.core.domain.document.PickedDocument

/**
 * Works out a book's content hash: the SHA-256 of its file, which is what names
 * it to a sync server and on every other device. See
 * docs/decisions/0002-content-hash-book-identity.md.
 *
 * Apart from [DocumentAnalyzer], although each platform's analyser is what does
 * it, because the import never hashes: reading every byte of a book is a cost
 * only a reader who syncs should pay.
 */
fun interface ContentHasher {

    /** Null for a file that cannot be read now - gone, or not permitted - to be tried again later. */
    suspend fun contentHash(document: PickedDocument): String?
}

/** The library's record of which books have been hashed, and as what. */
interface ContentHashStore {

    /** Books on the shelf whose file is there and whose hash is not known yet, oldest first. */
    suspend fun unhashed(): List<PickedDocument>

    /**
     * Records [contentHash] for the book at [location], and for the reading
     * sessions in it recorded before the hash was known.
     */
    suspend fun record(location: String, contentHash: String)
}
