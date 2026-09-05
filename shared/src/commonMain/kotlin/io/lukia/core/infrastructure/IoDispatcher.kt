package io.lukia.core.infrastructure

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Where work that waits belongs: reading a file, drawing a page through the
 * platform's own PDF renderer, and asking the database a question.
 *
 * The distinction that matters is against the default dispatcher, which is
 * sized to the number of cores because it is for work that uses one. Blocking
 * work put there occupies a core without using it, and the reader has one piece
 * of work that does this at scale: converting a PDF to text reads the file
 * through one handle per core at once, so on a four-core phone that conversion
 * *was* the default dispatcher, and everything else waited behind it.
 *
 * An expect rather than `Dispatchers.IO` written where it is wanted, because
 * that name is not one shared code can say: it is a plain property on the JVM
 * and an extension elsewhere, so common code naming it does not compile for
 * every platform this app is built for.
 */
internal expect fun ioDispatcher(): CoroutineDispatcher
