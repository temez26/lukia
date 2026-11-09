package io.lukia.features.stats.application

import io.lukia.core.domain.document.DocumentCatalogue
import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.reading.ReadingProgressRepository
import io.lukia.core.domain.reading.ReadingSessionRepository
import io.lukia.features.stats.domain.ReadingSummary
import io.lukia.features.stats.domain.readingHabits
import io.lukia.features.stats.domain.readingHistory
import io.lukia.features.stats.domain.readingStats
import io.lukia.features.stats.domain.yearBooks
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Reads the library, the positions stored against it and the sittings spent in
 * it, and reduces the three to what the statistics screen has to say.
 *
 * Reads the stores directly rather than borrowing the library screen's
 * ViewModel: the statistics have their own control, and on desktop they open
 * over a screen whose ViewModel is scoped to a different back stack entry. All
 * three reads are cheap next to what the grid already does with the same rows.
 *
 * The clock and the time zone are parameters with real defaults so that the
 * statistics can be tested on a fixed day in a fixed place - "how many days in a
 * row" is a question whose answer changes at midnight, and a test that depended
 * on when it ran would be a test that failed at midnight.
 */
class GetReadingStatsUseCase(
    private val library: DocumentCatalogue,
    private val progress: ReadingProgressRepository,
    private val sessions: ReadingSessionRepository,
    private val now: () -> Instant = { Clock.System.now() },
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    suspend operator fun invoke(): ReadingSummary {
        val here = zone()
        val today = now().toLocalDateTime(here).date
        val documents = library.getDocuments()
        val positions = progress.allProgress()
        val sittings = sessions.all()

        return ReadingSummary(
            stats = readingStats(documents, positions),
            habits = readingHabits(sittings, today, here),
            history = readingHistory(sittings, today, here),
            books = yearBooks(sittings, documents, positions, today, here),
        ).withCovers()
    }

    /**
     * The handful of books the screen actually shows, with their artwork.
     *
     * The library above is read without any - see [DocumentCatalogue.getDocuments]
     * - so the few that are drawn here are asked for one at a time. Reading a
     * year's books' covers is nothing beside reading every cover in the library
     * to draw them.
     */
    private suspend fun ReadingSummary.withCovers(): ReadingSummary = copy(
        books = books.copy(
            mostRead = books.mostRead.map { book ->
                book.copy(document = book.document?.withCover())
            },
            finished = books.finished.map { book ->
                book.copy(document = book.document.withCover())
            },
        ),
    )

    private suspend fun DocumentInfo.withCover(): DocumentInfo =
        copy(coverImage = library.coverFor(document.location))
}
