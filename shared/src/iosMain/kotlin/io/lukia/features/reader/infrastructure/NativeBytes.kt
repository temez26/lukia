package io.lukia.features.reader.infrastructure

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.Foundation.NSData
import platform.posix.memcpy

/**
 * Bytes across the boundary between Apple's frameworks and Kotlin, for the PDF
 * reading that goes through both halves of them - CoreFoundation for what is
 * read out of a file, Foundation for what UIKit hands back.
 */

@OptIn(ExperimentalForeignApi::class)
internal fun CFDataRef.toByteArray(): ByteArray =
    CFDataGetBytePtr(this)?.readBytes(CFDataGetLength(this).toInt()) ?: ByteArray(0)

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val result = ByteArray(length.toInt())

    if (result.isNotEmpty()) {
        result.usePinned { pinned ->
            memcpy(pinned.addressOf(0), bytes, length)
        }
    }

    return result
}
