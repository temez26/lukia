package io.lukia.features.library.domain

import io.lukia.core.domain.document.DocumentInfo

/**
 * The part of the shelf that mirrors a sync server's shared library: server
 * books not downloaded yet, and the mark on local books the server also has.
 * See docs/specs/sync-server.md.
 *
 * Apart from [LibraryRepository], whose callers import and remove books and
 * have no business with a server.
 */
interface ServerShelf {

    /** Where the book with these bytes is on this shelf, if it is. */
    suspend fun locationOf(contentHash: String): String?

    /**
     * Puts a server book on the shelf, not downloaded: [document]'s location is
     * where its download will land. A row already at that location is kept.
     */
    suspend fun addServerBook(document: DocumentInfo, contentHash: String)

    /**
     * The book at [location] is on the server now, as [contentHash] - which it
     * learns here, with everything a hash brings, if it was not hashed yet.
     */
    suspend fun markUploaded(location: String, contentHash: String)

    /** Marks every copy of these bytes on the shelf as on the server, or no longer. */
    suspend fun markOnServer(contentHash: String, onServer: Boolean)

    /** The server no longer has the book: its row goes if it was never downloaded. */
    suspend fun removeUndownloaded(contentHash: String)

    /**
     * The shelf without a server: rows never downloaded go, and nothing else
     * is marked as on one. For signing out.
     */
    suspend fun forgetServer()

    /** The bytes and file extension of the server book at [location], or null if it is not one. */
    suspend fun serverBookAt(location: String): Pair<String, String>?

    /** The download for [location] has landed. */
    suspend fun markDownloaded(location: String)

    /** The downloaded copy at [location] is gone: the server book waits to be downloaded again. */
    suspend fun markNotDownloaded(location: String)

    /** Where the library ends now - a mark that books added later are counted from. */
    suspend fun newestPosition(): Long

    /** Books added after [position] that are on this device and not on the server, oldest first. */
    suspend fun localOnlyAfter(position: Long): List<String>
}
