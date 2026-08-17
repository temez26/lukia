package io.lukia.features.bookdetail.presentation

import io.lukia.core.domain.BookMetadata
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.presentation.displayTitle
import io.lukia.features.bookdetail.application.BookDetails
import io.lukia.features.reader.domain.ReadingUnit

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
 * A language tag as a language.
 *
 * Only the primary subtag is looked at, so `en-GB` and `en-US` both read as
 * English - the region is a distinction for a spell checker rather than for
 * someone deciding whether they can read a book. A tag not in this list is shown
 * as it was written, upper-cased, which is still more use than hiding the row.
 */
fun BookMetadata.languageName(): String? {
    val tag = language?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val primary = tag.substringBefore('-').substringBefore('_').lowercase()

    return LanguageNames[primary] ?: tag.uppercase()
}

/**
 * The languages a reading app actually meets, by ISO 639-1 code.
 *
 * Deliberately not the full set of some two hundred: the rest fall back to their
 * tag, which is what a book in Aymara would have shown anyway had it been listed
 * and mistranslated.
 */
private val LanguageNames = mapOf(
    "ar" to "Arabic",
    "bg" to "Bulgarian",
    "ca" to "Catalan",
    "cs" to "Czech",
    "da" to "Danish",
    "de" to "German",
    "el" to "Greek",
    "en" to "English",
    "es" to "Spanish",
    "et" to "Estonian",
    "fa" to "Persian",
    "fi" to "Finnish",
    "fr" to "French",
    "he" to "Hebrew",
    "hi" to "Hindi",
    "hr" to "Croatian",
    "hu" to "Hungarian",
    "id" to "Indonesian",
    "is" to "Icelandic",
    "it" to "Italian",
    "ja" to "Japanese",
    "ko" to "Korean",
    "lt" to "Lithuanian",
    "lv" to "Latvian",
    "nb" to "Norwegian",
    "nl" to "Dutch",
    "nn" to "Norwegian",
    "no" to "Norwegian",
    "pl" to "Polish",
    "pt" to "Portuguese",
    "ro" to "Romanian",
    "ru" to "Russian",
    "sk" to "Slovak",
    "sl" to "Slovenian",
    "sr" to "Serbian",
    "sv" to "Swedish",
    "th" to "Thai",
    "tr" to "Turkish",
    "uk" to "Ukrainian",
    "vi" to "Vietnamese",
    "zh" to "Chinese"
)

/**
 * How long the book is, once that is known.
 *
 * Known only for a book that has been opened: the length travels with the stored
 * reading position rather than being read from the file, so the library can say
 * how far through a book someone is without opening it. A book nobody has opened
 * therefore has no length to show, and showing nothing is the honest answer -
 * the alternative is opening every book in the library to fill in a row.
 */
fun BookDetails.lengthLabel(): String? {
    val count = progress?.pageCount?.takeIf { it > 0 } ?: return null

    // A PDF has pages; a reflowable book has sections, and calling those pages
    // would promise a correspondence to a printed book that reflowed text does
    // not have. The unit is not stored, so it is inferred from the format the
    // same way the reader picks it.
    val unit = if (document.type == io.lukia.core.domain.DocumentType.PDF) {
        ReadingUnit.Page
    } else {
        ReadingUnit.Section
    }

    return if (count == 1) "1 ${unit.singular.lowercase()}" else "$count ${unit.plural}"
}

/**
 * Where the reader is in the book, in a sentence.
 *
 * Three states rather than a percentage in every case. A book nobody has opened
 * has no position to report and saying "0%" would suggest it had been started
 * and abandoned; a book that has been finished is worth saying so about outright.
 */
fun BookDetails.progressLabel(): String = when {
    isFinished -> "Finished"
    percentRead != null -> "$percentRead% read"
    else -> "Not started"
}

/** What pressing the cover would do, which depends on whether it has been started. */
fun BookDetails.readActionLabel(): String = when {
    isInProgress -> "Continue reading"
    isFinished -> "Read again"
    else -> "Start reading"
}
