package io.lukia.features.profile

import io.lukia.core.domain.FakeReadingProgressRepository
import io.lukia.core.domain.FakeReadingSessionRepository
import io.lukia.core.domain.ReadingSession
import io.lukia.features.library.FakeImportLibraryRepository
import io.lukia.features.library.book
import io.lukia.features.library.finished
import io.lukia.features.library.partRead
import io.lukia.features.profile.application.GetReadingSummaryUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** That the profile's figures come from both stores, joined on location. */
class GetReadingSummaryUseCaseTest {

    @Test
    fun countsTheLibraryAgainstThePositionsStoredForIt() = runTest {
        val halfway = book("Halfway")
        val done = book("Done")
        val waiting = book("Waiting")

        val library = FakeImportLibraryRepository(
            listOf(halfway.document, done.document, waiting.document),
        )
        val positions = FakeReadingProgressRepository()
        positions.save(halfway.document.document.location, partRead())
        positions.save(done.document.document.location, finished())

        val sessions = FakeReadingSessionRepository(
            listOf(ReadingSession(halfway.document.document.location, ANY_DAY, 30 * 60_000L)),
        )

        val summary = GetReadingSummaryUseCase(library, positions, sessions)()

        assertEquals(3, summary.stats.booksInLibrary)
        assertEquals(1, summary.stats.booksReading)
        assertEquals(1, summary.stats.booksFinished)
        assertEquals(1, summary.stats.booksUnread)
    }

    @Test
    fun anEmptyLibraryReportsNothingRatherThanFailing() = runTest {
        val summary = GetReadingSummaryUseCase(
            FakeImportLibraryRepository(),
            FakeReadingProgressRepository(),
            FakeReadingSessionRepository(),
        )()

        assertEquals(0, summary.stats.booksInLibrary)
        assertEquals(emptyList(), summary.recent)
        assertEquals(0L, summary.habits.totalMs)
    }

    /** The sittings are joined on too, which is the third store this reads. */
    @Test
    fun countsTheTimeSpentAsWellAsThePagesReached() = runTest {
        val book = book("Halfway")
        val sessions = FakeReadingSessionRepository(
            listOf(ReadingSession(book.document.document.location, ANY_DAY, 30 * 60_000L)),
        )

        val summary = GetReadingSummaryUseCase(
            FakeImportLibraryRepository(listOf(book.document)),
            FakeReadingProgressRepository(),
            sessions,
        )()

        assertEquals(30 * 60_000L, summary.habits.totalMs)
    }

    private companion object {

        /**
         * A moment in 2026, and nothing depends on which: what these tests check
         * is that the sittings are read at all, and when a sitting happened is
         * `ReadingHabitsTest`'s subject.
         */
        const val ANY_DAY = 1_756_000_000_000L
    }
}
