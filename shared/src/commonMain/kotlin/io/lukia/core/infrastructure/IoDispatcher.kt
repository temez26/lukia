package io.lukia.core.infrastructure

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Where work that waits belongs: reading a file, drawing a page through the
 * platform's own PDF renderer, and asking the database a question.
 *
 * The distinction is against the default dispatcher, sized to the core count
 * because it is for work that uses one. Blocking work put there occupies a core
 * without using it, and the reader has one piece that does this at scale:
 * converting a PDF to text reads the file through one handle per core, so on a
 * four-core phone that conversion *was* the default dispatcher.
 *
 * An expect rather than `Dispatchers.IO` written in place, because that name is
 * a plain property on the JVM and an extension elsewhere - common code naming it
 * does not compile for every platform.
 */
internal expect fun ioDispatcher(): CoroutineDispatcher
