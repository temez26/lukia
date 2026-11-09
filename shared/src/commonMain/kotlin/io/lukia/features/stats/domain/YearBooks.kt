package io.lukia.features.stats.domain

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.core.domain.reading.ReadingSession
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * A book and how long this year went on it.
 *
 * [document] is null for a book no longer on the shelf: its sittings outlive it
 * on purpose (see `ReadingSession.sq`), so it is still named, by [title].
 */
data class BookTime(
    val location: String,
    val title: String?,
    val durationMs: Long,
    val document: DocumentInfo? = null,
)

/**
 * A book read to its last page, dated by its last sitting.
 *
 * The app does not store when a book was finished. The last sitting is the day
 * the reader was last in it, which for a finished book is the day they reached
 * the end - unless they went back to it, when it moves to the later day.
 */
data class FinishedBook(
    val document: DocumentInfo,
    val finishedOn: LocalDate,
)

/** The books of one calendar year: where the time went, and what was finished. */
data class YearBooks(
    val year: Int,
    /** Most time first. */
    val mostRead: List<BookTime>,
    /** Most recently finished first. */
    val finished: List<FinishedBook>,
) {
    companion object {
        val Empty = YearBooks(year = 0, mostRead = emptyList(), finished = emptyList())
    }
}

/** How many books the most-read list names. */
private const val MostReadLimit = 5

internal fun yearBooks(
    sessions: List<ReadingSession>,
    documents: List<DocumentInfo>,
    progress: Map<String, ReadingProgress>,
    today: LocalDate,
    zone: TimeZone,
    mostReadLimit: Int = MostReadLimit,
): YearBooks {
    val shelf = documents.associateBy { it.document.location }
    val byBook = sessions.groupBy { it.location }

    val mostRead = byBook
        .mapNotNull { (location, sittings) ->
            val thisYear = sittings.filter { it.localDate(zone).year == today.year }
            val duration = thisYear.sumOf { it.durationMs }

            if (duration <= 0) return@mapNotNull null

            BookTime(
                location = location,
                title = sittings.lastOrNull { it.title != null }?.title,
                durationMs = duration,
                document = shelf[location],
            )
        }
        .sortedByDescending { it.durationMs }
        .take(mostReadLimit)

    // Only books on the shelf: a position goes with its book, so a removed book
    // cannot be known to have been finished.
    val finished = documents
        .filter { progress[it.document.location]?.isFinished == true }
        .mapNotNull { document ->
            byBook[document.document.location]
                ?.filter { it.durationMs > 0 }
                ?.maxByOrNull { it.startedAt }
                ?.localDate(zone)
                ?.takeIf { it.year == today.year }
                ?.let { FinishedBook(document, it) }
        }
        .sortedByDescending { it.finishedOn }

    return YearBooks(year = today.year, mostRead = mostRead, finished = finished)
}
