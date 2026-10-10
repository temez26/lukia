package io.lukia.features.reader.presentation.pagination

import kotlin.coroutines.CoroutineContext

/**
 * Where a book may be measured, and how much of it at once.
 *
 * Both answer one fact about the platform, not the device. Compose lays every
 * piece of text out - a page measured here, a label drawn by the UI - through
 * one process-wide cache of styles, and everywhere it draws with Skia that cache
 * is an unlocked map swept for dead keys on every lookup. Two threads laying
 * text out at once are two threads writing to it, and what comes back is a
 * ConcurrentModificationException from inside the layout. Thrown off the main
 * thread it ends the process rather than the book: the reader opens, and a
 * second later the app is gone.
 *
 * The two threads need not both be ours: a book measured on one background
 * thread still races the main one drawing the pages already measured, which is
 * the arrangement that crashed even after measuring was narrowed to one worker.
 *
 * So where the platform has that cache, the book is measured on the thread that
 * draws it, one run at a time, with nothing else laying text out beside it. The
 * measuring hands that thread back as it goes - see `Breather` - so the book
 * still arrives a run at a time, which is what somebody waiting notices.
 */
internal expect fun measuringContext(): CoroutineContext

/** How many of a book's runs may be measured at the same time - see [measuringContext]. */
internal expect fun maxParallelMeasurers(): Int
