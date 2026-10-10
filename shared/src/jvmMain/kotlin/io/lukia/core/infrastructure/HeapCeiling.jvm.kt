package io.lukia.core.infrastructure

internal actual fun heapCeilingBytes(): Long = Runtime.getRuntime().maxMemory()
