package io.lukia.core.infrastructure

import java.io.File
import java.io.IOException

internal actual fun readFileBytes(path: String): ByteArray = File(path).readBytes()

/**
 * Through a scratch file and into place, as [writeFileBytes]'s contract asks and
 * iOS's `atomically` gives for free: a write cut short by the process dying
 * leaves the old file rather than half a new one. Written straight to the
 * target, the reader would find a truncated container next launch, which every
 * format here reads as corrupt.
 *
 * Renamed rather than moved through `java.nio.file`, which Android gained only
 * at API 26 and desugaring does not back-fill - the same reason
 * `AndroidIncomingDocumentStore` puts its copies in place this way.
 *
 * Renamed straight over the old file rather than deleting it first: every
 * Android is Linux, where a rename replaces an existing name in one step. The
 * delete only opened a moment with no book under the name the library points at,
 * and made a failed rename lose both copies rather than one.
 */
internal actual fun writeFileBytes(path: String, bytes: ByteArray) {
    val target = File(path)
    val partial = File(target.parentFile, "${target.name}$PARTIAL_SUFFIX")

    try {
        partial.writeBytes(bytes)

        if (!partial.renameTo(target)) throw IOException("File could not be written: $path")
    } finally {
        partial.delete()
    }
}

internal actual fun fileSizeBytes(path: String): Long? = File(path).takeIf { it.isFile }?.length()

/** Matches the one the app's own document stores use, for the same reason. */
private const val PARTIAL_SUFFIX = ".part"
