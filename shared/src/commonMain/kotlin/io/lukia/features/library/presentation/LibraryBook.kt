package io.lukia.features.library.presentation

import androidx.compose.runtime.Immutable
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.presentation.displayTitle
import io.lukia.features.reader.domain.ReadingProgress

/** Where a book stands with its reader, which is what the shelves are made of. */
enum class ReadingStatus {
    Unread,
    Reading,
    Finished
}

/**
 * One book as the library screen deals with it: the stored entry, how far
 * through it its reader is, and the handful of strings the screen searches and
 * sorts by.
 *
 * The keys are worked out once, when the library is read, rather than each time
 * a letter is typed into the search field. Lower-casing a title is nothing; a
 * thousand of them on every keystroke, on the thread that draws, is the
 * difference between a search field that keeps up with typing and one that does
 * not.
 *
 * Progress is folded in here rather than looked up beside the grid because the
 * library asks about it constantly - three of the shelves are defined by it, one
 * of the sorts orders by it, and every card draws a bar from it - and a lookup
 * per book per pass is a map probe the screen can simply not do.
 */
@Immutable
data class LibraryBook(
    val document: DocumentInfo,
    /** Null for a book that has never been opened, which is not page one. */
    val progress: ReadingProgress? = null,
    /**
     * Everything the search field matches against, lower-cased and newline
     * separated: the title on the card, the title the document declares if it
     * differs, and everyone credited with writing it.
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
    val language: String? = null
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
fun List<DocumentInfo>.asLibrary(progress: Map<String, ReadingProgress>): List<LibraryBook> =
    map { document ->
        val metadata = document.metadata
        val title = document.displayTitle()
        val declaredTitle = metadata?.title?.takeIf { !it.equals(title, ignoreCase = true) }

        LibraryBook(
            document = document,
            progress = progress[document.document.location],
            searchKey = buildList {
                add(title)
                declaredTitle?.let(::add)
                metadata?.authors?.let(::addAll)
            }.joinToString(separator = "\n").lowercase(),
            sortTitle = title.lowercase(),
            sortAuthor = metadata?.authors?.firstOrNull()?.trim()?.lowercase().orEmpty(),
            authors = metadata?.authors.orEmpty().trimmedValues(),
            categories = metadata?.subjects.orEmpty().trimmedValues(),
            language = metadata?.language
                ?.substringBefore('-')
                ?.substringBefore('_')
                ?.trim()
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
        )
    }

/** Blank entries dropped and the rest tidied, for a list a filter offers as choices. */
private fun List<String>.trimmedValues(): List<String> =
    mapNotNull { it.trim().takeIf(String::isNotEmpty) }

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
