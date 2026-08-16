package io.lukia.core.infrastructure

import java.io.File

internal actual fun readFileBytes(path: String): ByteArray = File(path).readBytes()
