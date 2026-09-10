package io.lukia.features.reader.presentation

import io.lukia.core.domain.reading.FakeReadingSessionRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
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

    /**
     * A book left open on a desk stops writing as well as stops counting.
     *
     * Once a sitting has gone idle its length cannot grow, so the clock has
     * nothing left to bring - and a recorder that kept waking for it would
     * store the same number three times a minute for as long as the book lay
     * there.
     */
    @Test
    fun aBookLeftOpenStopsWritingOnceItHasGoneIdle() = runTest {
        val recorder = recorder()

        recorder.begin()
        time += 2.minutes
        recorder.onActivity()
        runCurrent()

        // Past the idle limit, and one tick to store the length it settled at.
        time += IdleAfter + 1.minutes
        advanceTimeBy(WriteEvery + 1.seconds)
        runCurrent()

        val settled = sessions.lengthsWritten

        // An hour of ticks with nobody reading.
        advanceTimeBy(WriteEvery * 180)
        runCurrent()

        assertEquals(settled, sessions.lengthsWritten)
        assertEquals(
            (2.minutes + IdleAfter).inWholeMilliseconds,
            sessions.all().single().durationMs,
        )
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

    /**
     * The book going behind something else ends the sitting rather than leaving
     * it running.
     *
     * What this is really pinning is the write on the way out: the clock is
     * stopped by cancelling the recording coroutine, and a write that gave up on
     * cancellation would lose everything since the last tick - which for a
     * reader who backgrounds the app mid-page is most of what they just read.
     */
    @Test
    fun leavingTheAppWritesDownWhatWasReadUpToThatMoment() = runTest {
        val recorder = recorder()

        recorder.begin()
        runCurrent()
        time += 45.seconds
        recorder.pause()
        runCurrent()

        assertEquals(45.seconds.inWholeMilliseconds, sessions.all().single().durationMs)
    }

    /**
     * And then stops. This is the whole of the finding: a book left open behind
     * another app went on waking every [WriteEvery] and storing the same number
     * again for the ten minutes it took [IdleAfter] to notice.
     */
    @Test
    fun leavingTheAppStopsTheClockRatherThanWaitingOutTheIdleTimeout() = runTest {
        val recorder = recorder()

        recorder.begin()
        runCurrent()
        time += 30.seconds
        recorder.pause()
        runCurrent()

        val writtenOnTheWayOut = sessions.lengthsWritten

        // Far past the idle timeout, which is when the clock used to give up.
        repeat(40) {
            time += WriteEvery
            advanceTimeBy(WriteEvery)
            runCurrent()
        }

        assertEquals(writtenOnTheWayOut, sessions.lengthsWritten, "no writes after leaving")
        assertEquals(30.seconds.inWholeMilliseconds, sessions.all().single().durationMs)
    }

    /**
     * Coming back is a new sitting, so the stretch spent elsewhere is credited to
     * nobody. Resuming the old one would have counted it.
     */
    @Test
    fun timeSpentAwayFromTheAppIsNotReadingTime() = runTest {
        val recorder = recorder()

        recorder.begin()
        runCurrent()
        time += 1.minutes
        recorder.pause()
        runCurrent()

        // Away for an hour.
        time += 60.minutes

        recorder.begin()
        runCurrent()
        time += 2.minutes
        recorder.onActivity()
        runCurrent()

        val sittings = sessions.all()

        assertEquals(2, sittings.size, "one sitting each side of the trip away")
        assertEquals(1.minutes.inWholeMilliseconds, sittings.first().durationMs)
        assertEquals(2.minutes.inWholeMilliseconds, sittings.last().durationMs)
    }

    /** A book that was never opened has no sitting for leaving to write down. */
    @Test
    fun leavingABookThatNeverStartedWritesNothing() = runTest {
        val recorder = recorder()

        recorder.pause()
        runCurrent()

        assertEquals(emptyList(), sessions.all())
    }
}
