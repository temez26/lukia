package io.lukia.features.reader.presentation.pagination

/**
 * How many of a book's runs may be measured at the same time.
 *
 * A question about the text stack rather than about the device, which is why it
 * is answered per platform and not by counting cores. Everything Compose draws
 * with Skia - iOS and the desktop both - lays text out through one cache of
 * styles shared by the whole process, and that cache is a plain map with no lock
 * on it that is swept for dead keys on every lookup. Two threads measuring at
 * once are two threads writing to it, and what comes back is a
 * ConcurrentModificationException from inside the layout - which, thrown where
 * the measuring happens, takes the app down with it rather than the book.
 *
 * So on those platforms a book is measured one run at a time. It still arrives a
 * run at a time - see [paginateProgressively] - and that, rather than how many
 * cores the runs are spread over, is what the reader is actually waiting on.
 *
 * Android has its own text stack, no such cache, and no such limit.
 */
internal expect fun maxParallelMeasurers(): Int
