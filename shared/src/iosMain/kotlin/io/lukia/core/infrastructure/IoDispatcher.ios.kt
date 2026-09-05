package io.lukia.core.infrastructure

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

/** An extension property here rather than a plain one, which is what the expect is for. */
internal actual fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
