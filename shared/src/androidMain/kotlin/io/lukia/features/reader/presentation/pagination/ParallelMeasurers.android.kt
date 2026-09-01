package io.lukia.features.reader.presentation.pagination

/**
 * Capped for the reasons `readersFor` is capped: a phone that has eight cores
 * does not have eight cores' worth of thermal headroom, and every worker holds a
 * measurer and the layouts it has cached. Past a handful the book is not
 * measured meaningfully sooner and the reader's battery notices.
 */
internal actual fun maxParallelMeasurers(): Int = 4
