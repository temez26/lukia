package io.lukia.features.reader.presentation.pagination

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * The main thread, which is the only one Compose lays text out on here without
 * the process ending - see the expect. It is also the thread the reader is drawn
 * on, which is what the measuring yields back to as it goes.
 */
internal actual fun measuringContext(): CoroutineContext = Dispatchers.Main

internal actual fun maxParallelMeasurers(): Int = 1
