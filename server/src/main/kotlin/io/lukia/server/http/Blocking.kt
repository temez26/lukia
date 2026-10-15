package io.lukia.server.http

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs JDBC calls and password hashing off the engine's request threads, which
 * are few and meant for I/O that suspends rather than blocks.
 */
suspend fun <T> blocking(block: () -> T): T = withContext(Dispatchers.IO) { block() }
