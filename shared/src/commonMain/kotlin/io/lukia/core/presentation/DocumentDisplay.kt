package io.lukia.core.presentation

import androidx.compose.runtime.Composable
import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.infrastructure.formatCount
import io.lukia.core.infrastructure.formatDecimal
import io.lukia.resources.Res
import io.lukia.resources.author_list_last
import io.lukia.resources.author_list_separator
import io.lukia.resources.book_on_server
import io.lukia.resources.book_unavailable
import io.lukia.resources.format_docx
import io.lukia.resources.format_epub
import io.lukia.resources.format_markdown
import io.lukia.resources.format_pdf
import io.lukia.resources.format_text
import io.lukia.resources.format_unknown
import io.lukia.resources.size_bytes
import io.lukia.resources.size_gigabytes
import io.lukia.resources.size_kilobytes
import io.lukia.resources.size_megabytes
import io.lukia.resources.size_terabytes
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What a book whose file is not on this device is called on its card: one the
 * sync server has, waiting to be downloaded, or one that went missing.
 */
fun DocumentInfo.statusLabel(): StringResource =
    if (awaitsDownload) Res.string.book_on_server else Res.string.book_unavailable

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

@Composable
fun DocumentInfo.formattedSize(): String = formatFileSize(sizeBytes)

/**
 * A file's size, in the largest unit that leaves a figure worth reading.
 *
 * The figure is formatted by the platform and the unit is a resource around it.
 * It used to be `"$rounded ${units[unitIndex]}"`, which wrote a full stop
 * wherever the app ran - "1.5 MB" is "1,5 MB" to most of Europe - and hung the
 * unit off the number in English word order.
 *
 * Kibibytes rather than kilobytes, which is what every file manager the reader
 * has already looked at this file in will have said.
 */
@Composable
fun formatFileSize(bytes: Long): String {
    if (bytes < BytesPerStep) {
        return stringResource(Res.string.size_bytes, formatCount(bytes.toInt()))
    }

    var value = bytes / BytesPerStep.toDouble()
    var unitIndex = 0

    while (value >= BytesPerStep && unitIndex < SizeUnits.lastIndex) {
        value /= BytesPerStep
        unitIndex++
    }

    return stringResource(SizeUnits[unitIndex], formatDecimal(value, fractionDigits = 1))
}

private const val BytesPerStep = 1024

/** In order, so the loop above steps through them by dividing. */
private val SizeUnits = listOf(
    Res.string.size_kilobytes,
    Res.string.size_megabytes,
    Res.string.size_gigabytes,
    Res.string.size_terabytes,
)

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
 * Both halves are resources - the separator between all but the last two, and
 * the phrase joining the final pair - because that is how lists differ between
 * languages: the joining word is not always a word, nor the separator a comma.
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
