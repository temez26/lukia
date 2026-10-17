package io.lukia.features.library.presentation.model

import io.ktor.http.decodeURLPart

private val IsbnPrefix = Regex("^(urn:)?isbn[:\\s]*", RegexOption.IGNORE_CASE)

private const val Isbn10Length = 10
private const val Isbn13Length = 13

/**
 * The ISBN [identifier] holds, without any `urn:isbn:` in front, or null when it
 * holds something else.
 *
 * An EPUB's identifier is as often a `urn:uuid:` its tooling made up as an
 * ISBN, and a UUID labelled ISBN on the details screen tells a reader nothing
 * true.
 */
fun isbnOrNull(identifier: String): String? {
    val isbn = identifier.trim().replace(IsbnPrefix, "")
    val compact = isbn.filterNot { it == '-' || it == ' ' }

    // The last of an ISBN-10's ten characters is a check digit, which may be X.
    val isIsbn10 = compact.length == Isbn10Length &&
        compact.dropLast(1).all(Char::isDigit) &&
        (compact.last().isDigit() || compact.last().equals('X', ignoreCase = true))
    val isIsbn13 = compact.length == Isbn13Length && compact.all(Char::isDigit)

    return isbn.takeIf { isIsbn10 || isIsbn13 }
}

/**
 * Where a book's file is, as a reader would recognise it.
 *
 * A file picked through Android's document picker is addressed by a
 * `content://` URI that percent-encodes the whole path into one segment; the
 * readable part is the document path after `/document/`, without the storage
 * volume in front of it. Anything that is not valid percent-encoding is shown
 * as it is rather than not at all.
 */
fun readableLocation(location: String): String {
    val path = if (location.startsWith("content://")) {
        location.substringAfterLast("/document/", location.substringAfterLast("/tree/"))
    } else {
        location
    }

    val decoded = runCatching { path.decodeURLPart() }.getOrDefault(path)

    return decoded.substringAfter("primary:")
}
