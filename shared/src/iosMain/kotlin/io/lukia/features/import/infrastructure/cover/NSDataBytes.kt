package io.lukia.features.import.infrastructure.cover

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/**
 * Bytes across the Foundation boundary, for the cover extraction that has to
 * hand documents to Apple's own readers and take images back.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData = memScoped {
    NSData.create(bytes = allocArrayOf(this@toNSData), length = this@toNSData.size.toULong())
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val length = this.length.toInt()
    val result = ByteArray(length)

    if (length > 0) {
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, length.toULong())
        }
    }

    return result
}
