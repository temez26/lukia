package io.lukia.features.profile.domain

import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.ReadingProgress

/**
 * How many books there are to read, how far the reading has got, and what is
 * being read at the moment.
 *
 * One value rather than a screenful of separate lookups, because every number on
 * the profile is a reading of the same two things - the library and the stored
 * positions in it - taken at the same moment. Read separately, the tiles could
 * disagree with the list underneath them about a book imported between the two.
 */
data class ReadingSummary(
    val stats: ReadingStats,
    /** Newest first. Empty for a reader who has not opened anything yet. */
    val recent: List<ReadingActivity>,
    /** What the reading has cost in hours, and when it happened. */
    val habits: ReadingHabits = ReadingHabits.Empty,
) {

    companion object {

        /** What a profile shows before its first read of the library lands. */
        val Empty = ReadingSummary(stats = ReadingStats.Empty, recent = emptyList())
    }
}

/**
 * The counted part: what a reader can be told about their reading without being
 * told about any particular book.
 *
 * Every number here is derived from what the app already stores and nothing is
 * tracked to produce them. There is no session log, no clock and no streak, and
 * deliberately so: the app records where each book was left off, which is enough
 * to say how much has been read and how much is left, and a "days in a row"
 * counter would need a device clock that can be wound backwards - see
 * `ReadingPosition.sq` for why this app does not trust one.
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
 * One book the reader has been in, as the profile lists it: the library's entry
 * for it and how far into it they got.
 *
 * The position is beside the book rather than looked up from it, because that
 * pairing is the whole of what this list is - the books that have a position at
 * all, in the order they were last written.
 */
data class ReadingActivity(
    val document: DocumentInfo,
    val progress: ReadingProgress,
) {
    val location: String get() = document.document.location
}

/**
 * How many books the profile lists as recent activity.
 *
 * Short on purpose. This is a reminder of what is on the go, not a history: the
 * library tab already orders the whole shelf by what was read last, and a
 * profile that reprinted it would be a second, worse library screen.
 */
private const val RecentLimit = 5

/**
 * Everything the profile screen shows about reading, worked out in one pass over
 * the library and the positions stored against it.
 *
 * A pure function of the two, so it is the same answer wherever it is asked and
 * can be tested without a database. Positions are joined onto the library rather
 * than counted on their own: a position can outlive the book it belongs to - the
 * store keys on location and takes no foreign key - and a reader who removed a
 * book from their library should not go on being credited with reading it.
 */
fun readingSummary(
    documents: List<DocumentInfo>,
    progress: Map<String, ReadingProgress>,
    habits: ReadingHabits = ReadingHabits.Empty,
    recentLimit: Int = RecentLimit,
): ReadingSummary {
    val read = documents.mapNotNull { document ->
        progress[document.document.location]?.let { document to it }
    }

    return ReadingSummary(
        stats = statsFor(documents, read.map { (_, progress) -> progress }),
        recent = read
            // By the store's own counter rather than by a clock, which is what
            // makes "last read" mean the same here as it does on the shelf.
            .sortedByDescending { (_, progress) -> progress.readAt }
            .take(recentLimit)
            .map { (document, progress) -> ReadingActivity(document, progress) },
        habits = habits,
    )
}

private fun statsFor(
    documents: List<DocumentInfo>,
    positions: List<ReadingProgress>,
): ReadingStats {
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
