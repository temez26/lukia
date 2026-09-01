package io.lukia.features.reader.infrastructure.text.epub

import io.lukia.features.reader.domain.ReflowableContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * [readEpub] for a test that is not itself a coroutine.
 *
 * On [Dispatchers.Default] rather than on the blocking thread, so that the files
 * really are read at the same time as each other here as they are in the app - a
 * test that read them one after another would be checking a different reader to
 * the one that ships.
 */
internal fun epubRead(bytes: ByteArray): ReflowableContent? =
    runBlocking(Dispatchers.Default) { readEpub(bytes) }
