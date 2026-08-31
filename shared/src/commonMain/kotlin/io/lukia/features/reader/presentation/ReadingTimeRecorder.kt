package io.lukia.features.reader.presentation

import io.lukia.core.domain.reading.ReadingSessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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
 * Generous on purpose. A page of dense prose in a second language - which is
 * what this app is for - can take several minutes, and an app that stopped its
 * clock after two would tell its most careful readers they had read for a
 * quarter of the time they had. What it guards against is the book left open on
 * a desk overnight, not the reader who is thinking.
 */
internal val IdleAfter: Duration = 10.minutes

/**
 * How often the sitting's length is written down when nothing else prompts it.
 *
 * Often, because there is no reliable moment to write it at the end: a ViewModel
 * being cleared cannot start work, and an app being killed does not ask. Writing
 * as it goes means what is lost is the last few seconds rather than the whole
 * sitting, and the write is one `UPDATE` of one integer.
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
 * A sitting begins when the book opens and runs until the reader has left it
 * alone for [IdleAfter]; turning a page after that begins a new one. That is the
 * whole model, and it is deliberately simple - the app cannot see whether
 * anybody is looking at the screen, so it counts the time a book was open and
 * being turned, and says so.
 *
 * All of it happens inside one coroutine, woken either by a page turn or by its
 * own clock. That is what keeps the sitting's state free of locks, and it is
 * also why the coroutine runs on [dispatcher] rather than on whatever the scope
 * came with: this is a timer and a database write, neither of which has any
 * business on the thread that draws.
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
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
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
        if (running != null) return

        // Marked here rather than inside the coroutine: the sitting started when
        // the book opened, not when a dispatcher got round to the recording.
        val opened = timeSource.markNow()

        running = scope.launch(dispatcher) { record(opened) }
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
                sitting = Sitting(timeSource.markNow())
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
