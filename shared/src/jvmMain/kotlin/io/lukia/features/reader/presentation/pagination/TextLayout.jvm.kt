package io.lukia.features.reader.presentation.pagination

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * Off the main thread still, which is where the desktop has always measured.
 *
 * The desktop draws with Skia and so shares the cache the expect describes -
 * here a `WeakHashMap`, which does not sweep itself on every lookup and so
 * leaves a far narrower window than iOS's does. One worker takes away the only
 * part of it this app was making worse; moving the measuring onto the event
 * thread as well would buy a crash nobody has reported at the price of a window
 * that stops painting while a book is opened.
 */
internal actual fun measuringContext(): CoroutineContext = Dispatchers.Default

internal actual fun maxParallelMeasurers(): Int = 1
