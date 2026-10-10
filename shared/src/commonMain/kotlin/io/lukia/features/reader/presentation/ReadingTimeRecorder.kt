package io.lukia.features.reader.presentation

import io.lukia.core.domain.reading.ReadingSessionRepository
import io.lukia.core.infrastructure.ioDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * How long a book has to be left alone before the reader is taken to have
 * stopped rather than to be reading slowly.
 *
 * Generous on purpose: a page of dense prose in a second language - what this
 * app is for - can take several minutes, and a clock stopping after two would
 * tell the most careful readers they had read a quarter of what they had. It
 * guards against the book left open overnight, not the reader who is thinking.
 */
internal val IdleAfter: Duration = 10.minutes

/**
 * How often the sitting's length is written down when nothing else prompts it.
 *
 * Often, because an app being killed does not ask. A sitting that ends in any of
 * the ordinary ways writes itself down as it goes - see `ReadingTimeRecorder.record`
 * - but a process that is taken away has no way to, so writing as it goes means
 * what is lost there is the last few seconds rather than the whole sitting. The
 * write is one `UPDATE` of one integer.
 */
internal val WriteEvery: Duration = 20.seconds

/**
 * One sitting, while it is happening.
 *
 * Only ever touched from inside the recording coroutine, which is what makes
 * plain `var`s safe here.
 */
private class Sitting(val start: TimeMark) {
    var lastActivity: TimeMark = start
    var id: Long? = null

    fun hasGoneIdle(): Boolean = lastActivity.elapsedNow() >= IdleAfter

    /**
     * How much of this sitting counts as reading: everything since it began,
     * less whatever it has since been sitting idle for.
     */
    fun counted(): Duration {
        val since = start.elapsedNow()
        val idle = lastActivity.elapsedNow()

        return if (idle >= IdleAfter) since - (idle - IdleAfter) else since
    }
}

/**
 * Records how long one book is read for.
 *
 * A sitting begins when the book opens and ends when the book is put away, when
 * the app goes behind something else - see [pause] - or when the reader has left
 * it alone for [IdleAfter]. A page turn after that last one begins a new sitting.
 *
 * Deliberately simple past that: the app cannot see whether anybody is looking at
 * a screen it is on the front of, so within a sitting it counts the time the book
 * was open and being turned, and says so.
 *
 * All inside one coroutine, woken by a page turn or by its own clock, which is
 * what keeps the sitting's state free of locks. It runs on [dispatcher] rather
 * than whatever the scope came with: this is a timer and a database write,
 * neither of which belongs on the thread that draws.
 *
 * Time is measured from a monotonic source rather than from the wall clock, so a
 * device that corrects its clock mid-chapter cannot record a sitting of minus an
 * hour. The wall clock is asked once per sitting, for the one thing a monotonic
 * source cannot say: which day it was.
 */
internal class ReadingTimeRecorder(
    private val location: String,
    private val sessions: ReadingSessionRepository,
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher = ioDispatcher(),
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val startedAtMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    /**
     * Page turns, as they happen.
     *
     * Conflated: what the recorder does with a turn is note that the reader is
     * still there, and two turns in the same instant are the same news. A
     * recorder that had not caught up would otherwise build a queue of them.
     */
    private val turns = Channel<Unit>(Channel.CONFLATED)

    private var running: Job? = null

    /**
     * The book is on screen, and its clock starts.
     *
     * Called once the document has actually opened, so a book that failed to
     * open is not recorded as having been read.
     */
    fun begin() {
        // A sitting that has ended leaves its job behind, so this asks whether
        // one is still running rather than whether one was ever started - which
        // is what lets a reader coming back to the book begin a new sitting.
        if (running?.isActive == true) return

        // Marked here rather than inside the coroutine: the sitting started when
        // the book opened, not when a dispatcher got round to the recording.
        val opened = timeSource.markNow()

        running = scope.launch(dispatcher) { record(opened) }
    }

    /**
     * The book is no longer in front of anybody - the app has gone behind
     * something else, or the reader has left the screen.
     *
     * The sitting ends here rather than running on. Without it the clock went on
     * counting an app nobody was looking at, and went on writing its length to
     * the database every [WriteEvery] until the idle timeout caught up ten
     * minutes later - some thirty wakeups and thirty writes to record time
     * nobody spent reading.
     *
     * What was read is written down on the way out, so ending a sitting costs
     * none of it - see [record]. Coming back begins a new sitting rather than
     * resuming this one, which is the point: the stretch in between was not
     * reading, and a resumed sitting would count it.
     *
     * The idle timeout stays for what this cannot see. A reader who leaves the
     * book open in front of them and walks away has not backgrounded anything,
     * and [IdleAfter] is still the only thing that notices.
     */
    fun pause() {
        running?.cancel()
        running = null
    }

    /**
     * The reader has turned a page, which is the only evidence this app has that
     * somebody is still there.
     *
     * Safe to call from anywhere: all it does is post to [turns].
     */
    fun onActivity() {
        turns.trySend(Unit)
    }

    private suspend fun record(opened: TimeMark) {
        var sitting = Sitting(opened)

        try {
            loop(sitting) { sitting = it }
        } finally {
            // However this coroutine came to an end - the book put away, the app
            // gone behind something, the reader leaving the screen - the sitting
            // ends with it and its length is written down.
            //
            // Uncancellable because being cancelled is the ordinary way in: a
            // write that gave up on cancellation would lose exactly the seconds
            // this exists to keep, which is what leaving the book used to cost.
            withContext(NonCancellable) { write(sitting) }
        }
    }

    /**
     * The sitting, until something ends it.
     *
     * Never returns: it is left by cancellation, which is what [record]'s
     * `finally` is there to catch. [restarted] hands back the sitting that
     * replaces one gone idle, so the write on the way out is about the sitting
     * actually in progress rather than the one this started with.
     */
    private suspend fun loop(first: Sitting, restarted: (Sitting) -> Unit) {
        var sitting = first

        // Whether what has been written down is all there is to write: the
        // sitting has gone idle, so its counted length has stopped growing.
        var settled = false

        while (true) {
            // Woken by a page turn, or by nothing at all once the writing
            // interval is up - both are a moment to write down where we are.
            //
            // A settled sitting waits only for the page turn. There is nothing
            // left for the clock to bring: a book left open on a desk would
            // otherwise wake three times a minute for as long as it lay there,
            // storing the same number it stored the time before.
            val turned = if (settled) {
                turns.receive()
                true
            } else {
                withTimeoutOrNull(WriteEvery) { turns.receive() } != null
            }

            if (turned && sitting.hasGoneIdle()) {
                // The old sitting ended when the reader stopped, hours ago; the
                // page just turned is the first of a new one.
                write(sitting)
                sitting = Sitting(timeSource.markNow()).also(restarted)
                settled = false
                continue
            }

            if (turned) sitting.lastActivity = timeSource.markNow()

            write(sitting)

            // Asked after the write rather than before it, so the length that
            // stops the clock is one that has been stored.
            settled = sitting.hasGoneIdle()
        }
    }

    /**
     * Writes what has been read so far, opening the row the first time there is
     * anything to write.
     *
     * What is written is the sitting's whole length rather than what has been
     * added since, so a write that never happened costs nothing - the next one
     * says the same thing, only more of it.
     */
    private suspend fun write(sitting: Sitting) {
        val counted = sitting.counted()

        if (counted <= Duration.ZERO) return

        val id = sitting.id
            ?: sessions.start(location, startedAtMillis()).also { sitting.id = it }

        sessions.extend(id, counted.inWholeMilliseconds)
    }
}
