package io.lukia.features.library.infrastructure.importing.metadata

import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.infrastructure.epub.collapseWhitespace
import io.lukia.core.infrastructure.pdf.ByteArrayPdfSource
import io.lukia.core.infrastructure.pdf.PdfObjectIndex
import io.lukia.core.infrastructure.pdf.asText

/**
 * A PDF's document information dictionary.
 *
 * Much the poorest of the three sources, and treated accordingly. The format has
 * no notion of a publisher or an ISBN, and the fields it does have are filled in
 * by whatever produced the file - which for most PDFs is a word processor or a
 * print driver rather than a publisher.
 *
 * So this is deliberately suspicious of what it reads. Producer and creator
 * strings are not taken as authorship at all, and a title that is plainly a file
 * name in disguise is dropped rather than shown to the reader as the book's -
 * "Microsoft Word - report_final_v3.docx" is the single most common `/Title` in
 * circulation, and showing it as a title is worse than showing none.
 *
 * This reaches across into the reader's PDF primitives on purpose. They are a
 * hand-written object parser that already knows how to find a dictionary in a
 * damaged file and decode its strings, and a second PDF parser living in the
 * import feature would be the same work done twice and wrong in different places.
 */
internal fun extractPdfMetadata(pdfBytes: ByteArray): BookMetadata? {
    val index = runCatching { PdfObjectIndex(ByteArrayPdfSource(pdfBytes)) }.getOrNull()
        ?: return null

    val info = index.info() ?: return null

    fun entry(key: String): String? = index.resolve(info[key])
        .asText
        ?.collapseWhitespace()
        ?.takeIf { it.isNotBlank() }

    val metadata = BookMetadata(
        title = entry("Title")?.takeIf { it.isPlausibleTitle() },
        authors = entry("Author").orEmpty().splitAuthors(),
        published = entry("CreationDate")?.asPdfYear(),
        description = entry("Subject"),
        subjects = entry("Keywords").orEmpty().splitKeywords(),
    )

    return metadata.takeIf { !it.isEmpty }
}

/**
 * Producers write the source document's file name into `/Title` far more often
 * than they write a real one. A value that still carries a file extension, or
 * that opens with the name of the application that made it, is one of those.
 */
private fun String.isPlausibleTitle(): Boolean {
    val trimmed = trim()

    return trimmed.length >= MinimumTitleLength && NotATitle.none { it(trimmed) }
}

private val ProducerPrefix = Regex(
    """^(microsoft\s+word|microsoft\s+powerpoint|microsoft\s+excel|untitled)\b""",
    RegexOption.IGNORE_CASE,
)

private val FileExtension = Regex(
    """\.(docx?|pdf|odt|rtf|tex|pages|indd|qxd)\s*$""",
    RegexOption.IGNORE_CASE,
)

private val HexBlob = Regex("""[0-9a-fA-F-]{16,}""")

/**
 * `/Author` is one string however many people it names, so the separators
 * publishers actually use are taken apart here.
 */
private fun String.splitAuthors(): List<String> =
    split(';', ',', '&')
        .flatMap { it.split(" and ", ignoreCase = true) }
        .map { it.collapseWhitespace() }
        .filter { it.isNotBlank() && it.length > 1 }
        .distinct()

private fun String.splitKeywords(): List<String> =
    split(',', ';')
        .map { it.collapseWhitespace() }
        .filter { it.isNotBlank() }
        .distinct()

/**
 * The year out of a PDF date string, which is written `D:YYYYMMDDHHmmSS` with
 * everything after the year optional. Only the year is kept: the rest is the
 * moment the file was generated, which for a scanned book is decades after it
 * was published and would be a lie to show as a publication date.
 */
private fun String.asPdfYear(): String? =
    PdfDate.find(this)?.groupValues?.get(1)

private val PdfDate = Regex("""^D?:?\s*(\d{4})""")

/**
 * The ways a `/Title` turns out to be something other than a title.
 *
 * A list rather than a run of guard clauses, so that what counts as a false
 * title reads as one thing and a fourth of them is one line.
 *
 * Two are matched anywhere in the value because their own patterns are anchored;
 * the third has to match the whole of it, since a title may legitimately mention
 * a hash without being one.
 */
private val NotATitle: List<(String) -> Boolean> = listOf(
    ProducerPrefix::containsMatchIn,
    FileExtension::containsMatchIn,
    // A bare UUID or hash, which is what a scanning workflow leaves behind.
    HexBlob::matches,
)

/** One character is an initial, not a title. */
private const val MinimumTitleLength = 2
