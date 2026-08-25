package io.lukia.features.reader.presentation

import io.lukia.features.reader.domain.FakeReadingSessionRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

/**
 * How long a sitting is taken to have lasted.
 *
 * Driven by hand rather than by waiting: the recorder writes on a tick and on
 * every page turn, and what is worth pinning is the arithmetic between those -
 * a test that slept for ten real minutes to check the idle rule would be a test
 * nobody runs.
 */
class ReadingTimeRecorderTest {

    private val time = TestTimeSource()
    private val sessions = FakeReadingSessionRepository()

    /**
     * Built on the background scope: the recorder writes on a tick that runs for
     * as long as the book is open, and a test that waited for that to finish
     * would wait for ever.
     */
    private fun TestScope.recorder(startedAt: Long = 1_000L) = ReadingTimeRecorder(
        location = "/library/book.pdf",
        sessions = sessions,
        scope = backgroundScope,
        // The test scheduler's own dispatcher, so the recording coroutine runs
        // when this test says it does rather than on a thread of its own.
        dispatcher = StandardTestDispatcher(testScheduler),
        timeSource = time,
        startedAtMillis = { startedAt },
    )

    @Test
    fun aBookOpenedAndNeverTurnedRecordsWhatItWasOpenFor() = runTest {
        val recorder = recorder()

        recorder.begin()
        time += 3.minutes
        recorder.onActivity()
        runCurrent()

        assertEquals(1, sessions.all().size)
        assertEquals(3.minutes.inWholeMilliseconds, sessions.all().single().durationMs)
    }

    @Test
    fun theSittingIsFiledUnderTheDayItStarted() = runTest {
        val recorder = recorder(startedAt = 1_724_000_000_000L)

        recorder.begin()
        time += 1.minutes
        recorder.onActivity()
        runCurrent()

        assertEquals(1_724_000_000_000L, sessions.all().single().startedAt)
    }

    /** One sitting is one row, however many times its length is rewritten. */
    @Test
    fun turningSeveralPagesExtendsOneSitting() = runTest {
        val recorder = recorder()

        recorder.begin()

        repeat(3) {
            time += 2.minutes
            recorder.onActivity()
            runCurrent()
        }

        assertEquals(1, sessions.all().size)
        assertEquals(6.minutes.inWholeMilliseconds, sessions.all().single().durationMs)
    }

    /**
     * A book left open on a desk stops counting where the reader stopped, rather
     * than crediting them with the whole night.
     */
    @Test
    fun timePastTheIdleLimitIsNotCounted() = runTest {
        val recorder = recorder()

        recorder.begin()
        time += 2.minutes
        recorder.onActivity()
        runCurrent()

        time += 3.hoursWorth()
        recorder.onActivity()
        runCurrent()

        val first = sessions.all().first()

        assertEquals((2.minutes + IdleAfter).inWholeMilliseconds, first.durationMs)
    }

    @Test
    fun turningAPageAfterALongPauseStartsANewSitting() = runTest {
        val recorder = recorder()

        recorder.begin()
        time += 1.minutes
        recorder.onActivity()
        runCurrent()

        time += 3.hoursWorth()
        recorder.onActivity()
        runCurrent()

        time += 5.minutes
        recorder.onActivity()
        runCurrent()

        assertEquals(2, sessions.all().size)
        assertEquals(5.minutes.inWholeMilliseconds, sessions.all().last().durationMs)
    }

    /** Nothing is written for a book that was opened and shut again at once. */
    @Test
    fun aBookOpenedForNoTimeAtAllIsNotASitting() = runTest {
        val recorder = recorder()

        recorder.begin()
        recorder.onActivity()
        runCurrent()

        assertEquals(emptyList(), sessions.all())
    }

    private fun Int.hoursWorth() = (this * 60).minutes
}
