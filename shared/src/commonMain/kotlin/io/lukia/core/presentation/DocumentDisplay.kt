package io.lukia.core.presentation

import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType

fun DocumentInfo.displayTitle(): String {
    val fileName = document.fileName
    val extension = document.extension

    return if (extension.isNotBlank() && fileName.endsWith(".$extension", ignoreCase = true)) {
        fileName.dropLast(extension.length + 1)
    } else {
        fileName
    }
}

fun DocumentInfo.displayType(): String = type.displayName()

/**
 * What to call a format where there is no document to hand - a filter offering
 * the formats a library holds, rather than a card describing one book.
 */
fun DocumentType.displayName(): String = when (this) {
    DocumentType.PDF -> "PDF"
    DocumentType.EPUB -> "EPUB"
    DocumentType.DOCX -> "Word"
    DocumentType.TEXT -> "Text"
    DocumentType.MARKDOWN -> "Markdown"
    DocumentType.UNKNOWN -> "Unknown"
}

fun DocumentInfo.formattedSize(): String = formatFileSize(sizeBytes)

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"

    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unitIndex = 0

    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }

    val rounded = (value * 10).let { kotlin.math.round(it) } / 10
    val formatted = if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString()
    }

    return "$formatted ${units[unitIndex]}"
}

/**
 * What to call the book.
 *
 * The document's own title where it declares one, and the file name otherwise.
 * A book that names itself is naming itself better than whoever saved the file
 * did - "Moby Dick; or, The Whale" against "moby-dick-2.epub" - and the formats
 * that carry a title are the ones where the file name is least likely to be the
 * real one.
 */
fun DocumentInfo.bookTitle(): String =
    metadata?.title?.takeIf { it.isNotBlank() } ?: displayTitle()

/**
 * Everyone credited, as one line.
 *
 * Two authors are joined with "and" rather than a comma, which is how a credit
 * reads aloud and how a cover sets it. Beyond two, commas take over and the last
 * still gets its "and" - the alternative reads as a list of unrelated names.
 */
fun BookMetadata.authorLine(): String? = when (authors.size) {
    0 -> null
    1 -> authors[0]
    2 -> "${authors[0]} and ${authors[1]}"
    else -> authors.dropLast(1).joinToString(", ") + " and " + authors.last()
}

/**
 * The year a book was published, out of whatever the document wrote.
 *
 * The formats permit a full ISO timestamp, a bare year, and most things in
 * between; a reader wants the year from all of them. Anything that does not
 * start with a plausible year is passed through untouched rather than dropped -
 * a book that says "Spring 1851" is telling the truth in its own way.
 */
fun BookMetadata.publishedYear(): String? {
    val raw = published?.trim()?.takeIf { it.isNotBlank() } ?: return null

    return YearPrefix.find(raw)?.groupValues?.get(1) ?: raw
}

private val YearPrefix = Regex("""^(1\d{3}|20\d{2}|21\d{2})\b""")

/**
 * The language the document declares, as a language rather than as a tag.
 *
 * The table it reads from is shared with the library's language filter - see
 * [languageName] - so a book shown as Norwegian here is filed under Norwegian
 * there.
 */
fun BookMetadata.languageName(): String? = languageName(language)
