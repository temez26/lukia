package io.lukia.core.infrastructure

import java.io.File
import java.io.IOException

internal actual fun readFileBytes(path: String): ByteArray = File(path).readBytes()

/**
 * Through a scratch file and into place, which is what the contract on
 * [writeFileBytes] asks for and what iOS's `atomically` gives it for free: a
 * write cut short by the process dying leaves the old file rather than half of a
 * new one. Written straight to the target, what the reader would find next
 * launch is a truncated container, which every format here reads as corrupt.
 *
 * Renamed rather than moved through `java.nio.file`, which Android only gained
 * at API 26 and desugaring does not back-fill - the same reason
 * `AndroidIncomingDocumentStore` puts its own copies in place this way.
 */
internal actual fun writeFileBytes(path: String, bytes: ByteArray) {
    val target = File(path)
    val partial = File(target.parentFile, "${target.name}$PARTIAL_SUFFIX")

    try {
        partial.writeBytes(bytes)
        target.delete()

        if (!partial.renameTo(target)) throw IOException("File could not be written: $path")
    } finally {
        partial.delete()
    }
}

internal actual fun fileSizeBytes(path: String): Long? = File(path).takeIf { it.isFile }?.length()

/** Matches the one the app's own document stores use, for the same reason. */
private const val PARTIAL_SUFFIX = ".part"
