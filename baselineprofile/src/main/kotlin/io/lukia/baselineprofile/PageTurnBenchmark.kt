package io.lukia.baselineprofile

import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.regex.Pattern

/**
 * What turning a page costs, in frames, on a real device.
 *
 * The app's most repeated action by a long way, and the one place where the
 * budget is "never late" rather than "fast enough": a dropped frame under a
 * finger dragging a page is the page catching on it.
 *
 * Frame timing rather than [ReaderOpenBenchmark]'s wall clock, because here the
 * frames *are* the question - the measured block scrolls and does nothing else,
 * so it reliably produces a run of them inside the traced window.
 *
 * Run it against a device that already holds the book below:
 *
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=io.lukia.baselineprofile.PageTurnBenchmark \
 *     -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
 * ```
 *
 * That last flag is not optional on a device somebody reads on, for the reason
 * spelled out in [ReaderOpenBenchmark]: without it the build uninstalls the app
 * and Android takes the whole library with it. Back the database up first.
 *
 * A real book rather than a fixture, for [ReaderOpenBenchmark]'s reason - so
 * this cannot run on an empty device, and is not part of `connectedCheck`.
 *
 * Two books, because a page turn is two different pieces of work and one number
 * over both would hide whichever is worse:
 *
 *  - a PDF page is *drawn* from the file at the width the window is now - see
 *    `RenderedPageCache`, which draws the next page ahead, so only a reader who
 *    outruns it waits.
 *  - a reflowed page was measured when the book was laid out, and has its text,
 *    marks and translations composed as it arrives.
 *
 * The first turn after opening is deliberately excluded: it is the opening still
 * finishing, which is [ReaderOpenBenchmark]'s question. [WARM_UP_TURNS] puts it
 * out of the way.
 */
class PageTurnBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** A page that has to be drawn out of the file before it can be read. */
    @Test
    fun turningAPageOfAPdf() = measureTurning(title = "Clean Code Collection")

    /**
     * Turns pages of the book [title] finds, and reports the frames it took.
     *
     * Opening the book happens in the setup rather than in the measurement:
     * that cost is [ReaderOpenBenchmark]'s, and a page turn measured with it
     * still in the window would be reporting the opening again under another
     * name.
     */
    private fun measureTurning(title: String) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        // Deliberately no startupMode, for the reason ReaderOpenBenchmark gives:
        // a cold one throws away the walk that got the book open. The process is
        // put back to nothing in the setup instead.
        setupBlock = {
            killProcess()
            searchFor(title)
            device.openBook(title)
            device.turnPages(WARM_UP_TURNS)
        },
    ) {
        device.turnPages(MEASURED_TURNS)
    }

    /** Presses the row [title] found, and waits for the book to be on screen. */
    private fun UiDevice.openBook(title: String) {
        val at = rowCentre(title)

        click(at.x, at.y)

        assertTrue(
            "No page arrived for “$title”",
            wait(Until.hasObject(By.desc(PAGE_LABEL)), OPENING_TIMEOUT_MILLIS),
        )
    }

    /**
     * Turns [times] pages, waiting for each to settle before asking for the next.
     *
     * A fling would measure how well the list keeps up with a gesture nobody
     * makes twice; what a reader does is move on one page and read it. So each
     * turn is a full-page scroll followed by the list coming to rest, which is
     * also what gives the frame timings a settled window to be collected over.
     *
     * The gesture margin keeps the swipe clear of the system's own edge
     * gestures, which would otherwise take it as a back or a Control Centre -
     * see `ReaderPageList`, whose page is edge to edge.
     */
    private fun UiDevice.turnPages(times: Int) {
        val pages = findObject(By.scrollable(true)) ?: error("No page list on screen")

        pages.setGestureMargin(displayWidth / GESTURE_MARGIN)

        repeat(times) {
            pages.scroll(Direction.DOWN, PAGE_FRACTION, SCROLL_SPEED)
            waitForIdle()
        }
    }

    private companion object {
        /** What a page of an open book calls itself - see `PageSheet` and `PageList`. */
        val PAGE_LABEL: Pattern = Pattern.compile("Page \\d+ of \\d+")

        const val ITERATIONS = 5

        /** Enough turns to leave the opening behind, and no more than that. */
        const val WARM_UP_TURNS = 2

        /**
         * Enough turns for the frame timings to have a distribution rather than
         * an anecdote, and few enough to stay inside one chapter of any book.
         */
        const val MEASURED_TURNS = 10

        /** One page, which is what the list is laid out in - see `PageSheet`. */
        const val PAGE_FRACTION = 1f

        /**
         * How fast the swipe travels, in pixels a second. Set rather than left to
         * the default so a run on a fast device and a run on a slow one are asking
         * the same question of both.
         */
        const val SCROLL_SPEED = 2_000

        /** Keeps the swipe clear of the system's own edge gestures. */
        const val GESTURE_MARGIN = 5

        const val OPENING_TIMEOUT_MILLIS = 120_000L
    }
}
