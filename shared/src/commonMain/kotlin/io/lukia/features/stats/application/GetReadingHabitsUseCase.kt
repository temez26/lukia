package io.lukia.features.stats.application

import io.lukia.core.domain.reading.ReadingSessionRepository
import io.lukia.features.stats.domain.ReadingHabits
import io.lukia.features.stats.domain.readingHabits
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Reads the sittings alone and reduces them to the reader's habits: the glance
 * at the week on Home, which has no use for the library or the positions that
 * [GetReadingStatsUseCase] reads beside them.
 *
 * The clock and the time zone are parameters for the same reason as there.
 */
class GetReadingHabitsUseCase(
    private val sessions: ReadingSessionRepository,
    private val now: () -> Instant = { Clock.System.now() },
    private val zone: () -> TimeZone = { TimeZone.currentSystemDefault() },
) {
    suspend operator fun invoke(): ReadingHabits {
        val here = zone()

        return readingHabits(
            sessions = sessions.all(),
            today = now().toLocalDateTime(here).date,
            zone = here,
        )
    }
}
