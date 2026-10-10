package io.lukia.features.library.infrastructure.importing.metadata

import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.infrastructure.ByteSource
import io.lukia.core.infrastructure.epub.collapseWhitespace
import io.lukia.core.infrastructure.pdf.PdfObjectIndex
import io.lukia.core.infrastructure.pdf.asText

/**
 * What a PDF says about itself: its information dictionary, and the one field
 * worth having that PDF never put there - see [declaredLanguage].
 *
 * Much the poorest of the three sources. The format has no notion of a publisher
 * or an ISBN, and the fields it does have are filled in by whatever produced the
 * file - for most PDFs a word processor or print driver, not a publisher.
 *
 * So this is deliberately suspicious: producer and creator strings are not taken
 * as authorship, and a title that is plainly a file name in disguise is dropped -
 * "Microsoft Word - report_final_v3.docx" is the commonest `/Title` in
 * circulation, and showing it is worse than showing none.
 *
 * It reaches across into the reader's PDF primitives on purpose: they already
 * know how to find a dictionary in a damaged file and decode its strings, and a
 * second PDF parser in the import feature would be the same work done twice and
 * wrong in different places.
 */
internal fun extractPdfMetadata(pdf: ByteSource): BookMetadata? {
    val index = runCatching { PdfObjectIndex(pdf) }.getOrNull()
        ?: return null

    // Not a reason to give up on the file: plenty of PDFs carry no information
    // dictionary at all and still say in their catalog what language they are
    // written in, which is the one thing here a reader is sent out of the app
    // with.
    val info = index.info()

    fun entry(key: String): String? = index.resolve(info?.get(key))
        .asText
        ?.collapseWhitespace()
        ?.takeIf { it.isNotBlank() }

    val metadata = BookMetadata(
        title = entry("Title")?.takeIf { it.isPlausibleTitle() },
        authors = entry("Author").orEmpty().splitAuthors(),
        published = entry("CreationDate")?.asPdfYear(),
        description = entry("Subject"),
        subjects = entry("Keywords").orEmpty().splitKeywords(),
        language = index.declaredLanguage(),
    )

    return metadata.takeIf { !it.isEmpty }
}

/**
 * What the document says it is written in.
 *
 * Alone among these in coming from the catalog rather than the information
 * dictionary: `/Lang` is a property of the document and PDF never gave `/Info` a
 * field for it. Read at all because a word held in a PDF reached a dictionary
 * with no source language - the site's own "any language" dash - while the same
 * word in an EPUB arrived with the pair the book declared.
 *
 * A BCP 47 tag left exactly as written, because the two things that ask - the
 * translator's prompt and a dictionary's address - want different parts of it.
 *
 * Not every PDF has one, and the dash is the honest answer there rather than a
 * guess at English.
 */
private fun PdfObjectIndex.declaredLanguage(): String? = resolve(catalog()?.get("Lang"))
    .asText
    ?.collapseWhitespace()
    ?.takeIf { it.isNotBlank() }

/**
 * Producers write the source document's file name into `/Title` far more often
 * than they write a real one. A value that still carries a file extension, or
 * that opens with the name of the application that made it, is one of those.
 */
private fun String.isPlausibleTitle(): Boolean {
    val trimmed = trim()

    return trimmed.isLongEnoughToBeATitle() && NotATitle.none { it(trimmed) }
}

/**
 * One character is an initial, not a title - in an alphabet. In a script that
 * writes a whole word in one character it is a book: 道 and 詩 are both titles
 * a publisher has actually used, and a rule counting characters was measuring
 * the alphabet rather than the book.
 */
private fun String.isLongEnoughToBeATitle(): Boolean = when (length) {
    0 -> false
    1 -> this[0].writesAWordAlone()
    else -> true
}

/** Kana and the CJK ideographs, then the hangul syllables. */
private fun Char.writesAWordAlone(): Boolean =
    code in 0x3040..0x9FFF || code in 0xAC00..0xD7AF

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
 * A list rather than a run of guard clauses, so what counts as a false title
 * reads as one thing and a fourth is one line.
 *
 * Two are matched anywhere in the value, their own patterns being anchored; the
 * third must match the whole of it, since a title may mention a hash without
 * being one.
 */
private val NotATitle: List<(String) -> Boolean> = listOf(
    ProducerPrefix::containsMatchIn,
    FileExtension::containsMatchIn,
    // A bare UUID or hash, which is what a scanning workflow leaves behind.
    HexBlob::matches,
)
