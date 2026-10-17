package io.lukia.core.infrastructure

import io.lukia.core.domain.document.IncomingDocument
import io.lukia.core.domain.document.IncomingDocumentStore
import io.lukia.core.domain.document.PickedDocument
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile

/**
 * Copies live in Application Support, beside the working copies and for the same
 * reason: they are the app's own state rather than files the reader put in the
 * Files app themselves.
 *
 * Not in Documents in particular: that is the folder `UIFileSharingEnabled`
 * publishes - see `Info.plist` - and a reader looking into it should find the
 * books they put there, not a hashed-name copy of everything ever shared in.
 *
 * The location handed back is a `file://` URL, the same shape a scanned folder's
 * books have, so the analyser and the workspace meet one shape either way.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSIncomingDocumentStore : IncomingDocumentStore {

    private val fileManager = NSFileManager.defaultManager

    private val directory: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(
            directory = NSApplicationSupportDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String ?: FALLBACK_DIRECTORY

        "$base/$INCOMING_DIRECTORY"
    }

    override suspend fun keep(document: IncomingDocument): PickedDocument =
        withContext(Dispatchers.IO) {
            val bytes = document.read()
            requireNotNull(bytes) { "Could not read ${document.fileName}" }

            val target = "$directory/" +
                IncomingDocumentNaming.fileName(bytes, document.extension)

            fileManager.createDirectoryAtPath(
                path = directory,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )

            write(bytes, target)

            PickedDocument(
                // The sender's name, not the one on disk: the shelf should show
                // the book the reader shared, not a hash of it.
                fileName = document.fileName,
                extension = document.extension,
                location = NSURL.fileURLWithPath(target).absoluteString.orEmpty(),
            )
        }

    /**
     * Takes over a file the document picker copied for the app, or returns null
     * when it could not be moved.
     *
     * The picker's copy sits in `tmp/<bundle>-Inbox`, which the system empties
     * within minutes, so a library row pointing there opens once at best. Moved
     * rather than copied: the file is already the app's, in the same sandbox, so
     * this is a rename however large the book.
     */
    fun adopt(url: NSURL): PickedDocument? {
        val source = url.path ?: return null
        val fileName = url.lastPathComponent ?: return null
        val extension = url.pathExtension.orEmpty()
        val target = "$directory/" +
            IncomingDocumentNaming.pickedName(NSUUID().UUIDString(), extension)

        fileManager.createDirectoryAtPath(
            path = directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )

        if (!fileManager.moveItemAtPath(source, toPath = target, error = null)) return null

        return PickedDocument(
            fileName = fileName,
            extension = extension,
            location = NSURL.fileURLWithPath(target).absoluteString.orEmpty(),
        )
    }

    /**
     * Only inside this directory. A book from a scanned folder is a `file://`
     * URL to somewhere in the Files app, and without the check this would delete
     * the reader's own copy of it rather than the app's.
     */
    override suspend fun discard(location: String) {
        withContext(Dispatchers.IO) {
            // A location arrives as a file:// URL, but a scanned one can be a
            // plain path - the same two shapes the workspace resolves.
            val path = NSURL.URLWithString(location)?.path ?: location

            if (path.substringBeforeLast('/') != directory) return@withContext

            fileManager.removeItemAtPath(path, null)
        }
    }

    /**
     * Writes through a scratch file and moves it into place, so a write cut
     * short by the process dying never leaves a half-written file under the name
     * the library is about to store.
     */
    private fun write(bytes: ByteArray, target: String) {
        val partial = "$target$PARTIAL_SUFFIX"

        try {
            fileManager.removeItemAtPath(partial, null)

            val written = bytes.toNSData().writeToFile(partial, atomically = true)
            check(written) { "Could not store a copy of $target" }

            fileManager.removeItemAtPath(target, null)

            val moved = fileManager.moveItemAtPath(partial, toPath = target, error = null)
            check(moved) { "Could not store a copy of $target" }
        } finally {
            fileManager.removeItemAtPath(partial, null)
        }
    }

    private companion object {
        const val PARTIAL_SUFFIX = ".part"

        /**
         * Application Support is always present in a real app sandbox; this only
         * covers the theoretical case of the lookup coming back empty.
         */
        const val FALLBACK_DIRECTORY = "/tmp"
    }
}

internal const val INCOMING_DIRECTORY = "incoming"
