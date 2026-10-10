package io.lukia.core.infrastructure

/**
 * The most memory this process will ever be allowed for its own objects.
 *
 * Asked beside [availableProcessors], and for the other half of the same
 * question: a phone has the cores to read a book through four handles long
 * before it has the heap to hold four copies of what each handle parses.
 */
internal expect fun heapCeilingBytes(): Long
