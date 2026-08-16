package io.lukia.features.library.presentation

import io.lukia.core.domain.DocumentInfo
import io.lukia.core.presentation.displayTitle

/**
 * The books in this library matching [query], in their existing order.
 *
 * Matching is on the title as it is shown rather than on the file name behind it:
 * a reader searching for what they can see on the card should not have to know
 * that the file it came from carries an extension.
 *
 * A blank query matches everything, so callers need no branch of their own.
 */
fun List<DocumentInfo>.matchingQuery(query: String): List<DocumentInfo> {
    val trimmed = query.trim()

    if (trimmed.isEmpty()) return this

    return filter { it.displayTitle().contains(trimmed, ignoreCase = true) }
}
