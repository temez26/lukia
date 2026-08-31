package io.lukia.core.infrastructure

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.storage.DocumentWorkspace
import io.lukia.core.domain.storage.WorkingCopy
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * Copies live in Application Support rather than Documents: they are the app's
 * own working state, not files the user put there or should see in the Files
 * app beside their originals.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSDocumentWorkspace : DocumentWorkspace {

    private val fileManager = NSFileManager.defaultManager

    private val directory: String by lazy {
        val base = NSSearchPathForDirectoriesInDomains(
            directory = NSApplicationSupportDirectory,
            domainMask = NSUserDomainMask,
            expandTilde = true,
        ).firstOrNull() as? String ?: FALLBACK_DIRECTORY

        "$base/$WORKSPACE_DIRECTORY"
    }

    override suspend fun workingCopy(document: DocumentInfo): WorkingCopy =
        withContext(Dispatchers.IO) {
            val location = document.document.location
            val target = "$directory/" +
                WorkingCopyNaming.fileName(location, document.document.extension)

            // A zero-length copy is treated as absent rather than reused: it is
            // what a copy interrupted before the move would leave behind.
            if (sizeOf(target) <= 0L) {
                val source = resolvePath(location)

                require(fileManager.fileExistsAtPath(source)) {
                    "File does not exist: $location"
                }

                fileManager.createDirectoryAtPath(
                    path = directory,
                    withIntermediateDirectories = true,
                    attributes = null,
                    error = null,
                )

                copyInto(source, target)
            }

            WorkingCopy(sourceLocation = location, path = target)
        }

    override suspend fun discard(location: String) {
        withContext(Dispatchers.IO) {
            val prefix = WorkingCopyNaming.prefix(location)

            fileManager.contentsOfDirectoryAtPath(directory, null)
                ?.filterIsInstance<String>()
                ?.filter { it.startsWith(prefix) }
                ?.forEach { fileManager.removeItemAtPath("$directory/$it", null) }
        }
    }

    /**
     * Copies through a scratch file and moves it into place, so a copy cut short
     * by the process dying never leaves a half-written file under the name
     * everything downstream trusts.
     */
    private fun copyInto(source: String, target: String) {
        val partial = "$target$PARTIAL_SUFFIX"

        try {
            fileManager.removeItemAtPath(partial, null)

            val copied = fileManager.copyItemAtPath(source, toPath = partial, error = null)
            check(copied) { "Could not store a copy of $source" }

            fileManager.removeItemAtPath(target, null)

            val moved = fileManager.moveItemAtPath(partial, toPath = target, error = null)
            check(moved) { "Could not store a copy of $source" }
        } finally {
            fileManager.removeItemAtPath(partial, null)
        }
    }

    /**
     * A picked document is addressed by a file:// URL, but a scanned one can
     * arrive as a plain path - both have to resolve to something the file
     * manager understands.
     */
    private fun resolvePath(location: String): String =
        NSURL.URLWithString(location)?.path ?: location

    private fun sizeOf(path: String): Long {
        if (!fileManager.fileExistsAtPath(path)) return 0L

        return (fileManager.attributesOfItemAtPath(path, null)?.get(NSFileSize) as? NSNumber)
            ?.longLongValue
            ?: 0L
    }

    private companion object {
        const val WORKSPACE_DIRECTORY = "documents"
        const val PARTIAL_SUFFIX = ".part"

        /**
         * Application Support is always present in a real app sandbox; this only
         * covers the theoretical case of the lookup coming back empty.
         */
        const val FALLBACK_DIRECTORY = "/tmp"
    }
}
