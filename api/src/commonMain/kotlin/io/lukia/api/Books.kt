package io.lukia.api

import kotlinx.serialization.Serializable

/**
 * A book in the server's shared library, as the app learns of it.
 *
 * [id] is the SHA-256 of the file's bytes - see
 * docs/decisions/0002-content-hash-book-identity.md - so a device can tell
 * whether a file it already holds is this book without downloading anything.
 *
 * [type] is the app's own document type by name (`EPUB`, `PDF`…), and the
 * metadata is what the file says about itself, read by the server on upload.
 */
@Serializable
data class BookResponse(
    val id: String,
    val fileName: String,
    val type: String,
    val sizeBytes: Long,
    val hasCover: Boolean,
    val title: String? = null,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val published: String? = null,
    val language: String? = null,
    val description: String? = null,
    val subjects: List<String> = emptyList(),
    val identifier: String? = null,
    val addedAt: Long,
    /**
     * True for a book removed from the library since the cursor the app asked
     * from. Sent rather than left out, because an absent book cannot tell a
     * device to take it off its shelf.
     */
    val deleted: Boolean = false,
)
