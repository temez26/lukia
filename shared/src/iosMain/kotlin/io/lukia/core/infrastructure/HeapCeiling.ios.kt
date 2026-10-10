package io.lukia.core.infrastructure

/**
 * No ceiling of its own: a process here is held to what the device has rather
 * than to a heap size set for it, so there is no number to divide by.
 */
internal actual fun heapCeilingBytes(): Long = Long.MAX_VALUE
