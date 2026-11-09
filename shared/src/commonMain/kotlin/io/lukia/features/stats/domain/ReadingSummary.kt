package io.lukia.features.stats.domain

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.reading.ReadingProgress

/**
 * How many books there are to read, how far the reading has got, and where the
 * time went.
 *
 * One value rather than a screenful of separate lookups, because every number on
 * this screen is a reading of the same things - the library, the stored
 * positions in it and the sittings spent in it - taken at the same moment. Read
 * separately, the tiles could disagree with the books listed under them about a
 * book imported between the two.
 */
data class ReadingSummary(
    val stats: ReadingStats,
    /** What the reading has cost in hours, and when it happened. */
    val habits: ReadingHabits = ReadingHabits.Empty,
    /** Every day read on, for paging through weeks, months and years. */
    val history: ReadingHistory = ReadingHistory.Empty,
    /** This year's most-read and finished books. */
    val books: YearBooks = YearBooks.Empty,
) {

    companion object {

        /** What the screen shows before its first read of the library lands. */
        val Empty = ReadingSummary(stats = ReadingStats.Empty)
    }
}

/**
 * The counted part: what a reader can be told about their reading without being
 * told about any particular book.
 *
 * Every number is derived from the positions the app already stores, and nothing
 * is tracked to produce them: where each book was left off says how much has
 * been read and how much is left. Anything needing a clock - hours, days in a
 * row - is in [ReadingHabits] instead, counted from recorded sittings.
 */
data class ReadingStats(
    val booksInLibrary: Int,
    /** Opened, and not yet at the last page. */
    val booksReading: Int,
    val booksFinished: Int,
    /** Never opened. Not the same as being on page one. */
    val booksUnread: Int,
    val favourites: Int,
    /**
     * Pages reached, added up over every book that has been opened.
     *
     * Reached rather than read: what is stored is the furthest position in each
     * book, so this counts the pages behind the reader in every book they have
     * started. A book skipped to the end of counts as read to the end of it,
     * which is the honest reading of the only thing the app knows.
     */
    val pagesRead: Int,
    /**
     * How far through the whole library, from 0 to 1, counting a book never
     * opened as none of it. Null for a library with nothing in it.
     */
    val libraryFraction: Float?,
) {

    /** True while there is nothing to summarise, which is a new reader's state. */
    val isEmpty: Boolean get() = booksInLibrary == 0

    companion object {
        val Empty = ReadingStats(
            booksInLibrary = 0,
            booksReading = 0,
            booksFinished = 0,
            booksUnread = 0,
            favourites = 0,
            pagesRead = 0,
            libraryFraction = null,
        )
    }
}

/**
 * The library's counts, worked out in one pass over the library and the
 * positions stored against it.
 *
 * Positions are joined onto the library rather than counted alone, so a book
 * removed from the library stops being credited. The schema now says the same -
 * a position is deleted with its book - but this stays a join, because a pure
 * function of two lists should not depend on who wrote them.
 */
internal fun readingStats(
    documents: List<DocumentInfo>,
    progress: Map<String, ReadingProgress>,
): ReadingStats {
    val positions = documents.mapNotNull { document -> progress[document.document.location] }
    val finished = positions.count { it.isFinished }

    return ReadingStats(
        booksInLibrary = documents.size,
        booksReading = positions.size - finished,
        booksFinished = finished,
        booksUnread = documents.size - positions.size,
        favourites = documents.count { it.isFavorite },
        pagesRead = positions.sumOf { it.pageNumber ?: 0 },
        // Averaged over the whole library rather than over what has been opened,
        // so importing a book moves the figure down. That is the question this
        // answers: how much of what I own have I read, not how far into the ones
        // I picked up.
        libraryFraction = documents
            .takeIf { it.isNotEmpty() }
            ?.let { positions.sumOf { position -> (position.fraction ?: 0f).toDouble() } }
            ?.let { total -> (total / documents.size).toFloat() },
    )
}
