package io.lukia.core.infrastructure

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
internal actual fun readFileBytes(path: String): ByteArray {
    val data = NSFileManager.defaultManager.contentsAtPath(path)
        ?: throw IllegalArgumentException("File could not be read: $path")

    val bytes = ByteArray(data.length.toInt())

    if (bytes.isNotEmpty()) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
    }

    return bytes
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun writeFileBytes(path: String, bytes: ByteArray) {
    val data = memScoped {
        NSData.create(bytes = allocArrayOf(bytes), length = bytes.size.toULong())
    }

    // Through a scratch file and into place, which is what `atomically` asks
    // for: a write cut short by the app being killed leaves the old file rather
    // than half of a new one.
    require(data.writeToFile(path, atomically = true)) { "File could not be written: $path" }
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun fileSizeBytes(path: String): Long? {
    val attributes = NSFileManager.defaultManager.attributesOfItemAtPath(path, null) ?: return null

    return (attributes[NSFileSize] as? NSNumber)?.longLongValue
}
