package io.lukia.core.infrastructure

import java.io.File

internal actual fun readFileBytes(path: String): ByteArray = File(path).readBytes()

internal actual fun writeFileBytes(path: String, bytes: ByteArray) {
    File(path).writeBytes(bytes)
}

internal actual fun fileSizeBytes(path: String): Long? = File(path).takeIf { it.isFile }?.length()
