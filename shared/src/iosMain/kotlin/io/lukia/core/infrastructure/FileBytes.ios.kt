package io.lukia.core.infrastructure

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSFileManager
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
