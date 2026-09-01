package io.lukia.features.reader.presentation.pagination

/** Skia's shared style cache is a `WeakHashMap` here, and no safer for it. */
internal actual fun maxParallelMeasurers(): Int = 1
