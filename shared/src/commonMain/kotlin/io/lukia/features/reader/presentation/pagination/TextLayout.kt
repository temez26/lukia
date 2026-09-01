package io.lukia.features.reader.presentation.pagination

import kotlin.coroutines.CoroutineContext

/**
 * Where a book may be measured, and how much of it at once.
 *
 * Both are answers to one fact about the platform rather than about the device.
 * Compose lays every piece of text out - a page being measured here, a label
 * being drawn by the UI - through one cache of styles belonging to the whole
 * process, and on everything it draws with Skia that cache is an ordinary map
 * with no lock on it, swept for dead keys on every lookup. Two threads laying
 * text out at the same time are two threads writing to it, and what comes back
 * is a ConcurrentModificationException thrown from inside the layout. Thrown
 * where the measuring happens, off the main thread, it ends the process rather
 * than the book: the reader opens, and a second later the app is gone.
 *
 * The two threads need not both be ours. A book measured on one background
 * thread still races the main one, which is drawing the pages that have already
 * been measured - and that is the arrangement that crashed on a device even
 * after the measuring had been narrowed to a single worker.
 *
 * So where the platform has that cache, the book is measured on the same thread
 * that draws it, one run at a time. Nothing is then laying text out beside
 * anything else. That thread is the one the app draws on, so the measuring hands
 * it back as it goes - see `Breather` - and the book still arrives a run at a
 * time, which is what somebody waiting on it actually notices.
 */
internal expect fun measuringContext(): CoroutineContext

/** How many of a book's runs may be measured at the same time - see [measuringContext]. */
internal expect fun maxParallelMeasurers(): Int
