package io.lukia.features.reader.infrastructure

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef

/**
 * Bytes out of a CoreFoundation data, for the PDF reading that reads files
 * through it. Foundation's half of the same boundary is core's - see
 * `NSDataBytes.kt`.
 */

@OptIn(ExperimentalForeignApi::class)
internal fun CFDataRef.toByteArray(): ByteArray =
    CFDataGetBytePtr(this)?.readBytes(CFDataGetLength(this).toInt()) ?: ByteArray(0)
