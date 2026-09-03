package io.lukia.core.infrastructure.diagnostics

import io.lukia.core.domain.diagnostics.CrashStore
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * Application Support, beside the database and the working copies, for the same
 * reason they are there: it is the app's own state rather than something the
 * reader put there or should meet in the Files app.
 */
@OptIn(ExperimentalForeignApi::class)
class IOSCrashStore : CrashStore {

    private val path: String?
        get() {
            val directory = NSSearchPathForDirectoriesInDomains(
                directory = NSApplicationSupportDirectory,
                domainMask = NSUserDomainMask,
                expandTilde = true,
            ).firstOrNull() as? String ?: return null

            NSFileManager.defaultManager.createDirectoryAtPath(
                path = directory,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            )

            return "$directory/$FILE_NAME"
        }

    override fun write(report: String) {
        val target = path ?: return

        (report as NSString).writeToFile(
            path = target,
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
    }

    override fun read(): String? {
        val target = path ?: return null

        return NSString.stringWithContentsOfFile(
            path = target,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
    }

    override fun discard() {
        val target = path ?: return

        NSFileManager.defaultManager.removeItemAtPath(target, error = null)
    }

    private companion object {
        const val FILE_NAME = "last-crash.txt"
    }
}
