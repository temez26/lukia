package io.lukia.core.presentation

import androidx.compose.runtime.Composable
import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.resources.Res
import io.lukia.resources.author_list_last
import io.lukia.resources.author_list_separator
import io.lukia.resources.format_docx
import io.lukia.resources.format_epub
import io.lukia.resources.format_markdown
import io.lukia.resources.format_pdf
import io.lukia.resources.format_text
import io.lukia.resources.format_unknown
import org.jetbrains.compose.resources.stringResource

fun DocumentInfo.displayTitle(): String {
    val fileName = document.fileName
    val extension = document.extension

    return if (extension.isNotBlank() && fileName.endsWith(".$extension", ignoreCase = true)) {
        fileName.dropLast(extension.length + 1)
    } else {
        fileName
    }
}

@Composable
fun DocumentInfo.displayType(): String = type.displayName()

/**
 * What to call a format where there is no document to hand - a filter offering
 * the formats a library holds, rather than a card describing one book.
 */
@Composable
fun DocumentType.displayName(): String = when (this) {
    DocumentType.PDF -> stringResource(Res.string.format_pdf)
    DocumentType.EPUB -> stringResource(Res.string.format_epub)
    DocumentType.DOCX -> stringResource(Res.string.format_docx)
    DocumentType.TEXT -> stringResource(Res.string.format_text)
    DocumentType.MARKDOWN -> stringResource(Res.string.format_markdown)
    DocumentType.UNKNOWN -> stringResource(Res.string.format_unknown)
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
 *
 * Both halves are resources: the separator between all but the last two, and the
 * phrase that joins the final pair. Split that way because that is how a list
 * differs between languages - the joining word is not always a word, and the
 * separator is not always a comma.
 */
@Composable
fun BookMetadata.authorLine(): String? {
    if (authors.isEmpty()) return null

    if (authors.size == 1) return authors[0]

    val separator = stringResource(Res.string.author_list_separator)

    return stringResource(
        Res.string.author_list_last,
        authors.dropLast(1).joinToString(separator),
        authors.last(),
    )
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
