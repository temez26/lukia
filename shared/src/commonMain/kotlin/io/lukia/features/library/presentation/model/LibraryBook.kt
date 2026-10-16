package io.lukia.features.library.presentation.model

import androidx.compose.runtime.Immutable
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.presentation.bookTitle
import io.lukia.core.presentation.displayTitle
import io.lukia.shared.presentation.components.cover.CoverArtSource

/** Where a book stands with its reader, which is what the shelves are made of. */
enum class ReadingStatus {
    Unread,
    Reading,
    Finished,
}

/**
 * One book as the library screen deals with it: the stored entry, how far
 * through it its reader is, and the handful of strings the screen searches and
 * sorts by.
 *
 * The keys are worked out once, when the library is read, rather than on every
 * keystroke. Lower-casing a title is nothing; a thousand of them per keystroke
 * on the thread that draws is the difference between a search field that keeps
 * up with typing and one that does not.
 *
 * Progress is folded in rather than looked up beside the grid because the
 * library asks constantly - three shelves are defined by it, one sort orders by
 * it, every card draws a bar from it - and this spares a map probe per book per
 * pass.
 */
@Immutable
data class LibraryBook(
    val document: DocumentInfo,
    /** Null for a book that has never been opened, which is not page one. */
    val progress: ReadingProgress? = null,
    /**
     * Everything the search field matches against, lower-cased and newline
     * separated: the title on the card, the file's name if it differs, and
     * everyone credited with writing it.
     */
    val searchKey: String = "",
    /** The title as the card shows it, lower-cased, for ordering. */
    val sortTitle: String = "",
    /** The first author lower-cased, or blank for a book that credits nobody. */
    val sortAuthor: String = "",
    /** Everyone credited, as written and trimmed. */
    val authors: List<String> = emptyList(),
    /** Whatever the document filed itself under, as written. */
    val categories: List<String> = emptyList(),
    /** The primary language subtag, lower-cased - `en` for both `en-GB` and `en`. */
    val language: String? = null,
    /**
     * How to get this book's artwork, which is not the artwork itself.
     *
     * The shelf reads its books without their covers - see
     * [io.lukia.core.domain.document.DocumentCatalogue.getDocuments] - because
     * a library's worth of them is hundreds of megabytes to draw the dozen the
     * reader can see. What travels with the book instead is the way to ask for
     * one, and the asking happens as a card is drawn.
     */
    val cover: CoverArtSource? = null,
) {
    val location: String get() = document.document.location

    val status: ReadingStatus
        get() = when {
            progress == null -> ReadingStatus.Unread
            progress.isFinished -> ReadingStatus.Finished
            else -> ReadingStatus.Reading
        }
}

/**
 * The library as the screen works with it, with each book's search and sort keys
 * worked out and its reading position joined on.
 *
 * Done in one pass over the whole library rather than lazily per book: this runs
 * off the main thread once per load, and the alternative is the same work spread
 * over the frames that draw the grid.
 */
fun List<DocumentInfo>.asLibrary(
    progress: Map<String, ReadingProgress>,
    /**
     * Where each book's artwork is to be found. Supplied rather than reached
     * for, so that narrowing and ordering a library stays something a test can
     * do without a database behind it.
     */
    coverFor: (location: String) -> CoverArtSource? = { null },
): List<LibraryBook> =
    map { document ->
        val metadata = document.metadata
        val title = document.bookTitle()
        val fileTitle = document.displayTitle().takeIf { !it.equals(title, ignoreCase = true) }

        LibraryBook(
            document = document,
            cover = coverFor(document.document.location),
            progress = progress[document.document.location],
            searchKey = buildList {
                add(title)
                fileTitle?.let(::add)
                metadata?.authors?.let(::addAll)
            }.joinToString(separator = "\n").lowercase(),
            sortTitle = title.lowercase(),
            sortAuthor = metadata?.authors?.firstOrNull()?.trim()?.lowercase().orEmpty(),
            authors = metadata?.authors.orEmpty().trimmedValues(),
            categories = metadata?.subjects.orEmpty().categoryValues(),
            language = metadata?.language
                ?.substringBefore('-')
                ?.substringBefore('_')
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() },
        )
    }

/** Blank entries dropped and the rest tidied, for a list a filter offers as choices. */
private fun List<String>.trimmedValues(): List<String> =
    mapNotNull { it.trim().takeIf(String::isNotEmpty) }

/**
 * Subjects as a reader would file them.
 *
 * Split on semicolons, because some files pack every keyword into one subject
 * ("Sapkowski; Witcher; Sword"), and with anything that has no letters dropped:
 * a bare number is a catalogue code, not a subject anybody browses by.
 */
private fun List<String>.categoryValues(): List<String> =
    flatMap { it.split(';') }
        .trimmedValues()
        .filter { value -> value.any(Char::isLetter) }
        // Capitalised only when written all in lower case, so "cryptography"
        // sits beside "Computer Science" without "iOS" becoming "IOS".
        .map { value -> if (value == value.lowercase()) value.replaceFirstChar(Char::titlecase) else value }

/**
 * What two spellings of one category have in common: case, apostrophes and
 * stray spacing set aside, so "Artist's books" and "Artists' books" file
 * together.
 */
internal fun categoryKey(value: String): String =
    value.lowercase()
        .filter { it.isLetterOrDigit() || it.isWhitespace() }
        .split(Whitespace)
        .filter(String::isNotEmpty)
        .joinToString(" ")

private val Whitespace = Regex("\\s+")

/** Whether this book is filed under [category] - see [categoryKey]. */
internal fun List<String>.filesUnder(category: String): Boolean {
    val key = categoryKey(category)
    return any { categoryKey(it) == key }
}

/**
 * Whether this book carries [value], without regard to case.
 *
 * Case-insensitive because these come out of files rather than out of a
 * vocabulary: one book files itself under `Fiction` and the next under
 * `fiction`, and a filter that treated those as two things would show the same
 * row twice and put half the books behind each.
 */
internal fun List<String>.holds(value: String): Boolean =
    any { it.equals(value, ignoreCase = true) }
