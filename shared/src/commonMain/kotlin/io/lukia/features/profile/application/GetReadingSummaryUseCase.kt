package io.lukia.features.profile.application

import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.profile.domain.ReadingSummary
import io.lukia.features.profile.domain.readingHabits
import io.lukia.features.profile.domain.readingSummary
import io.lukia.features.reader.domain.ReadingProgressRepository
import io.lukia.features.reader.domain.ReadingSessionRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Reads the library, the positions stored against it and the sittings spent in
 * it, and reduces the three to what a profile has to say.
 *
 * It reads the stores directly rather than borrowing the library screen's
 * ViewModel: the profile is reached from its own control rather than from the
 * shelf, and on desktop it opens over a screen whose ViewModel is scoped to a
 * different back stack entry. All three reads are cheap next to what the grid
 * already does with the same rows.
 *
 * The clock and the time zone are parameters with real defaults so that the
 * statistics can be tested on a fixed day in a fixed place - "how many days in a
 * row" is a question whose answer changes at midnight, and a test that depended
 * on when it ran would be a test that failed at midnight.
 */
class GetReadingSummaryUseCase(
    private val library: LibraryRepository,
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
        )
    }
}
