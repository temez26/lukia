package io.lukia.baselineprofile

import android.os.SystemClock
import android.util.Log
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.regex.Pattern

/**
 * How long a book takes to become readable, measured on a real device.
 *
 * The number a reader actually waits through: pressing a book to its first page
 * being on screen. Timed here rather than read off a metric because no metric
 * the harness collects knows what "the book is open" means - a spinner and an
 * unlaid-out sheet are both not it. A page carrying its own "Page n of m" is.
 *
 * Run it against a device that already holds the two books below:
 *
 * ```
 * ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=io.lukia.baselineprofile.ReaderOpenBenchmark \
 *     -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
 * ```
 *
 * The last flag is not optional on a device somebody reads on. Without it the
 * build uninstalls the app when the run finishes, and Android takes the library
 * with it - every book, every position, and the folder permission. Back the
 * database up before running this anyway:
 *
 * ```
 * adb shell run-as io.lukia cat databases/lukia.db > lukia.db
 * ```
 *
 * Frame timings come out in the benchmark's own report; the opening times are
 * logged under [LOG_TAG], being wall-clock measurements this takes itself.
 *
 * Measured against a book already in the library rather than a shipped fixture,
 * which would measure the generator's idea of a book. The cost is that this
 * cannot run on an empty device, so it is not part of `connectedCheck`. Point it
 * at a book of your own by adding a test beside the one below, named by whatever
 * the library's search finds it by.
 *
 * A PDF read as text is deliberately not covered: that opening waits on every
 * page's text being read out of the file, which no layout can shorten, and needs
 * a reader who already chose to read it that way - a setting rather than a fact
 * about the file.
 */
@OptIn(ExperimentalMetricApi::class)
class ReaderOpenBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /**
     * A long EPUB, which is the case where the whole cost is the app's own: the
     * file is parsed and then laid out into pages, and nothing is drawn until
     * both have finished for the whole book.
     */
    @Test
    fun openingALongEpub() = measureOpening(title = "Clean Code Collection")

    /**
     * Opens the book [title] finds, and reports how long its first page took.
     *
     * The search happens in the setup rather than in the measurement: finding a
     * book among two hundred of them is the library's work, and this is about
     * the reader. What is measured begins at the press that opens the book.
     */
    private fun measureOpening(title: String) = rule.measureRepeated(
        packageName = PACKAGE,
        // Not what this is for - the opening is timed below - but the other
        // half of the same question: holding a long book as measured pages is
        // memory, and this is what it costs.
        //
        // Deliberately not frame timing: a benchmark that walks through the app
        // does not reliably produce frames inside the traced window, and a
        // metric that sometimes collects nothing fails the run.
        metrics = listOf(MemoryUsageMetric(MemoryUsageMetric.Mode.Max)),
        iterations = ITERATIONS,
        // Deliberately no startupMode. A cold one kills the app between the
        // setup and the measurement, which is right for measuring a launch and
        // wrong for measuring anything reached by walking through the app - the
        // walk would be thrown away and the measurement taken on the launcher.
        // The process is put back to nothing below instead.
        setupBlock = {
            killProcess()
            searchFor(title)
        },
    ) {
        val at = device.rowCentre(title)

        val took = withoutWaitingForIdle {
            val started = SystemClock.uptimeMillis()

            device.click(at.x, at.y)

            val opened = device.wait(Until.hasObject(By.desc(PAGE_LABEL)), OPENING_TIMEOUT_MILLIS)

            assertTrue("No page arrived for \u201C$title\u201D", opened)

            SystemClock.uptimeMillis() - started
        }

        Log.i(LOG_TAG, "$title: first page after ${took}ms")
    }

    /**
     * Runs [block] with the screen answered as it is asked rather than once the
     * app has nothing left to do.
     *
     * Only around the measurement, never around the walk that sets it up. A book
     * still being laid out is never idle, so a measurement taken through the
     * usual wait would be charged for exactly the work this exists to move off
     * the reader's wait. Everywhere else the wait is what makes a query land on
     * a screen that has stopped moving.
     */
    private fun <T> withoutWaitingForIdle(block: () -> T): T {
        val configurator = Configurator.getInstance()
        val waited = configurator.waitForIdleTimeout

        configurator.waitForIdleTimeout = 0

        return try {
            block()
        } finally {
            configurator.waitForIdleTimeout = waited
        }
    }

    private companion object {
        const val LOG_TAG = "ReaderOpenBenchmark"

        /** What a page of an open book calls itself - see `PageSheet` and `PageList`. */
        val PAGE_LABEL: Pattern = Pattern.compile("Page \\d+ of \\d+")

        const val ITERATIONS = 5

        /** A long book laid out from end to end is the thing being measured. */
        const val OPENING_TIMEOUT_MILLIS = 120_000L
    }
}
