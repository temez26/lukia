package io.lukia.features.reader.infrastructure

import io.lukia.core.infrastructure.fileSizeBytes
import io.lukia.core.infrastructure.readFileBytes
import io.lukia.core.infrastructure.writeFileBytes
import io.lukia.features.reader.domain.page.PageSize

/**
 * Where a PDF's page sizes are kept, so that they are measured once per file.
 *
 * Measuring them means opening every page through the platform's renderer, and
 * the reader cannot lay a page out until it has them all: a 1,118-page textbook
 * spent 2.8 seconds on it, every time it was opened, before its first page could
 * be drawn. The sizes of a file's pages never change, so the second opening
 * reads them back instead.
 *
 * Kept beside the working copy under its name, like a converted book - see
 * `storedReflow` - so discarding a document takes its sizes with it. The copy's
 * length and the page count guard against a note left from another file.
 *
 * Best effort throughout: sizes that cannot be read or written are measured
 * again, which is what used to happen every time.
 */
internal fun storedPageSizes(path: String, pageCount: Int): List<PageSize>? {
    val sourceSize = fileSizeBytes(path) ?: return null

    return runCatching {
        val lines = readFileBytes(path.asPageSizesPath()).decodeToString().lines()
        if (lines.first().toLong() != sourceSize) return null

        val sizes = lines.drop(1).filter(String::isNotBlank).map { line ->
            val (width, height) = line.split(' ').map(String::toFloat)
            PageSize(width = width, height = height)
        }
        sizes.takeIf { it.size == pageCount }
    }.getOrNull()
}

internal fun storePageSizes(path: String, sizes: List<PageSize>) {
    val sourceSize = fileSizeBytes(path) ?: return

    val record = buildString {
        appendLine(sourceSize)
        sizes.forEach { appendLine("${it.width} ${it.height}") }
    }

    runCatching { writeFileBytes(path.asPageSizesPath(), record.encodeToByteArray()) }
}

/** Appended rather than swapped for `.pdf`, so the name keeps the working copy's prefix. */
private fun String.asPageSizesPath(): String = "$this.pagesizes"
