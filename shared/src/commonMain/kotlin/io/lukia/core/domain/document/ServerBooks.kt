package io.lukia.core.domain.document

import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf

/*
 * What the library needs from the sync server, said in the library's own
 * terms so the library never depends on the feature that talks to it. See
 * docs/specs/sync-server.md.
 */

/**
 * Where downloaded books are written, a piece at a time, so a book of any size
 * costs one piece of memory.
 *
 * In the same place each platform keeps the copies of books handed to the app,
 * so a downloaded book is removed the same way one of those is, and is served
 * the same way to the platform's readers.
 */
interface BookFileStore {

    /**
     * Where the book with [contentHash] lives once downloaded - known before it
     * is, so the book can be on the shelf before its bytes are on the device.
     */
    fun locationFor(contentHash: String, extension: String): String

    /** Starts writing that book. Nothing is at [locationFor] until [BookFileWriter.commit]. */
    suspend fun open(contentHash: String, extension: String): BookFileWriter
}

interface BookFileWriter {

    suspend fun write(bytes: ByteArray, count: Int)

    /** Puts the whole file where [BookFileStore.locationFor] said it would be. */
    suspend fun commit()

    /** Throws away what was written, for a download that failed or was cancelled. */
    suspend fun abort()
}

/**
 * Fetches a server book that is on the shelf but not yet on the device.
 * Reports how far it has got, from 0 to 1, as it goes; throws an
 * [io.lukia.core.domain.AppErrorException] when it cannot.
 */
fun interface ServerBookDownloader {
    suspend fun download(location: String, onProgress: (Float) -> Unit)
}

/**
 * Sends a book on this device to the sync server's shared library, where every
 * account sees it. Reports how far it has got, from 0 to 1, as it goes; throws
 * an [io.lukia.core.domain.AppErrorException] when it cannot.
 */
interface ServerBookUploader {

    /** Whether there is a server to send to - true while signed in. */
    val available: Flow<Boolean>

    suspend fun upload(location: String, onProgress: (Float) -> Unit)

    /** For the screens and tests that never meet a server. */
    object None : ServerBookUploader {
        override val available: Flow<Boolean> = flowOf(false)

        override suspend fun upload(location: String, onProgress: (Float) -> Unit) =
            throw AppErrorException(AppError.DocumentNotFound)
    }
}

/**
 * Says that the shelf changed behind the screen showing it - books arrived from
 * or left with the sync server - so the screen reads it again.
 */
class ShelfChanges {

    private val announced = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val changes: SharedFlow<Unit> = announced.asSharedFlow()

    fun announce() {
        announced.tryEmit(Unit)
    }
}
