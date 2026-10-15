package io.lukia.core.infrastructure

import io.lukia.core.domain.document.BookFileStore
import io.lukia.core.domain.document.BookFileWriter
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.closeFile
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.writeData

/**
 * Downloaded books, in Application Support beside the copies of books handed
 * to the app - see [IOSIncomingDocumentStore] - and addressed by the same
 * `file://` URLs, so the reader opens one exactly as it opens the other.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSBookFileStore : BookFileStore {

    private val fileManager = NSFileManager.defaultManager

    private val directory: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(
            directory = NSApplicationSupportDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String ?: FallbackDirectory

        "$base/$INCOMING_DIRECTORY"
    }

    override fun locationFor(contentHash: String, extension: String): String =
        NSURL.fileURLWithPath(pathFor(contentHash, extension)).absoluteString.orEmpty()

    override suspend fun open(contentHash: String, extension: String): BookFileWriter = withContext(Dispatchers.IO) {
        fileManager.createDirectoryAtPath(
            directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        val target = pathFor(contentHash, extension)
        val partial = "$target$PartialSuffix"

        fileManager.removeItemAtPath(partial, null)
        check(fileManager.createFileAtPath(partial, contents = null, attributes = null)) { "Could not start $target" }
        val handle = checkNotNull(NSFileHandle.fileHandleForWritingAtPath(partial)) { "Could not open $target" }

        HandleBookWriter(handle, partial, target)
    }

    private fun pathFor(contentHash: String, extension: String) =
        "$directory/${IncomingDocumentNaming.serverBookName(contentHash, extension)}"

    private companion object {
        const val PartialSuffix = ".part"

        /** Application Support is always present in a real sandbox; see [IOSIncomingDocumentStore]. */
        const val FallbackDirectory = "/tmp"
    }
}

/** Writes to [partial], and moves it to [target] only once the whole book is there. */
@OptIn(ExperimentalForeignApi::class)
private class HandleBookWriter(
    private val handle: NSFileHandle,
    private val partial: String,
    private val target: String,
) : BookFileWriter {

    private val fileManager = NSFileManager.defaultManager

    override suspend fun write(bytes: ByteArray, count: Int) = withContext(Dispatchers.IO) {
        handle.writeData(bytes.copyOf(count).toNSData())
    }

    override suspend fun commit() = withContext(Dispatchers.IO) {
        handle.closeFile()
        fileManager.removeItemAtPath(target, null)
        check(fileManager.moveItemAtPath(partial, toPath = target, error = null)) { "Could not store $target" }
    }

    override suspend fun abort() = withContext(Dispatchers.IO) {
        handle.closeFile()
        fileManager.removeItemAtPath(partial, null)
        Unit
    }
}
