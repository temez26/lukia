package io.lukia.features.stats.application

import io.lukia.core.domain.document.DocumentCatalogue
import io.lukia.core.domain.reading.ReadingProgressRepository
import io.lukia.core.domain.reading.ReadingSessionRepository
import io.lukia.features.stats.domain.ReadingSummary
import io.lukia.features.stats.domain.readingHabits
import io.lukia.features.stats.domain.readingSummary
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Reads the library, the positions stored against it and the sittings spent in
 * it, and reduces the three to what the statistics screen has to say.
 *
 * It reads the stores directly rather than borrowing the library screen's
 * ViewModel: the statistics are reached from their own control rather than from
 * the shelf, and on desktop they open over a screen whose ViewModel is scoped to
 * a different back stack entry. All three reads are cheap next to what the grid
 * already does with the same rows.
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

        return readingSummary(
            documents = library.getDocuments(),
            progress = progress.allProgress(),
            habits = readingHabits(
                sessions = sessions.all(),
                today = now().toLocalDateTime(here).date,
                zone = here,
            ),
        ).withRecentCovers()
    }

    /**
     * The handful of books the screen actually shows, with their artwork.
     *
     * The library above is read without any - see [DocumentCatalogue.getDocuments]
     * - so the few that are drawn here are asked for one at a time. There are
     * five of them: reading five covers is nothing beside reading every cover in
     * the library to draw five.
     */
    private suspend fun ReadingSummary.withRecentCovers(): ReadingSummary = copy(
        recent = recent.map { activity ->
            activity.copy(
                document = activity.document.copy(coverImage = library.coverFor(activity.location)),
            )
        },
    )
}
