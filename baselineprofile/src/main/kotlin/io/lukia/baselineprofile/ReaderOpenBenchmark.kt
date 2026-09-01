package io.lukia.baselineprofile

import android.graphics.Point
import android.os.SystemClock
import android.util.Log
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.regex.Pattern

/**
 * How long a book takes to become readable, measured on a real device.
 *
 * The number this exists for is the one a reader actually waits through: the
 * time between pressing a book and the first page of it being on screen. It is
 * timed here rather than read off a metric because no metric the harness
 * collects knows what "the book is open" means - a reader can be looking at a
 * spinner, or at a sheet the book has not been laid out onto yet, and neither of
 * those is the book being open. A page carrying its own "Page n of m" is.
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
 * with it - every book, every position, and the permission the app was granted
 * to read the folder they came from. The measurement is worth nothing next to
 * that, so back the database up before running this anyway:
 *
 * ```
 * adb shell run-as io.lukia cat databases/lukia.db > lukia.db
 * ```
 *
 * The frame timings come out in the benchmark's own report; the opening times
 * are logged under [LOG_TAG], because they are wall-clock measurements this
 * takes itself rather than metrics the harness collected.
 *
 * Measured against a book already in the library rather than a fixture shipped
 * beside this. What is worth measuring is the reading of a real book - a
 * ten-megabyte collection of four - and a generated one would measure the
 * generator's idea of a book instead. The cost is that this cannot run on an
 * empty device, which is why it is not part of `connectedCheck`. Point it at a
 * book of your own by adding a test beside the one below; the title is whatever
 * the library's search finds it by.
 *
 * A PDF read as text is deliberately not covered. What that opening waits on is
 * every page's text being read out of the file, which is a cost this measures
 * nothing about and which no layout can shorten - and it needs a book the reader
 * has already chosen to read as text, which is a setting rather than a fact
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
        // Not what this is for - the opening is timed below - but it is the
        // other half of the same question: holding a long book as pages that
        // have been measured is memory, and this is what it costs.
        //
        // Deliberately not frame timing. A benchmark that walks through the app
        // does not reliably produce frames inside the window being traced, and a
        // metric that sometimes collects nothing fails the run rather than
        // reporting one.
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
     * Only around the measurement, and never around the walk that sets it up. A
     * book still being laid out behind the page somebody is reading is never
     * idle, so a measurement taken through the usual wait would be charged for
     * exactly the work this exists to move off the reader's wait - and the more
     * of it there was, the better the reading would look. Everywhere else the
     * wait is what makes a query land on a screen that has stopped moving.
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

    /**
     * Where to press to open [title], as a point rather than as a node.
     *
     * A node goes stale the moment the list it is in is rebuilt, and this list
     * is rebuilt as the search narrows - so what is carried into the measurement
     * is a place on the screen, which cannot go stale. Read again on a stale
     * node rather than failing: the row is there, it is the handle on it that
     * expired.
     */
    private fun UiDevice.rowCentre(title: String): Point {
        val row = By.clazz(RESULT_ROW).textContains(title)

        repeat(ROW_ATTEMPTS) {
            runCatching { wait(Until.findObject(row), ARRIVAL_TIMEOUT_MILLIS)?.visibleCenter }
                .getOrNull()
                ?.let { return it }
        }

        error("No row for \u201C$title\u201D. On screen: ${visibleText()}")
    }

    /**
     * Puts the library's search screen in front of the book called [title].
     *
     * Everything here is setup rather than measurement: finding a book among two
     * hundred of them is the library's work, and this is about the reader.
     */
    private fun MacrobenchmarkScope.searchFor(title: String) {
        pressHome()
        startActivityAndWait()

        // The opening animation and the first read of the library both run here
        // - see LaunchSplash - so this sits through them rather than reaching
        // for a tab bar that has not arrived.
        device.wait(Until.hasObject(By.desc(SEARCH_TAB)), ARRIVAL_TIMEOUT_MILLIS)

        // An app killed inside a book comes back inside it, and a book has no
        // tab bar. Backing out of the one the last iteration opened is not part
        // of what is being measured.
        var attempts = 0

        while (!device.hasObject(By.desc(SEARCH_TAB)) && attempts++ < BACK_ATTEMPTS) {
            device.pressBack()
            device.wait(Until.hasObject(By.desc(SEARCH_TAB)), ARRIVAL_TIMEOUT_MILLIS)
        }

        // The term outlives the book being opened and closed, so every iteration
        // after the first finds its row already listed and types nothing.
        if (device.hasObject(By.clazz(RESULT_ROW).textContains(title))) return

        // By description rather than by text: the bar labels each tab as one
        // node - see `MobileNavigationBar` - and the word inside it is a label
        // rather than the thing that takes the press.
        device.findObject(By.desc(SEARCH_TAB)).click()
        device.wait(Until.hasObject(By.clazz(TEXT_FIELD)), ARRIVAL_TIMEOUT_MILLIS)

        // Typed at the field rather than written into it. Setting the text
        // through the accessibility node does not reach a Compose text field
        // reliably, and a search that never narrowed leaves nothing to press.
        device.findObject(By.clazz(TEXT_FIELD)).click()
        device.executeShellCommand("input text " + title.replace(" ", "%s"))

        // The list narrows as the term arrives, so what this waits for is the
        // row rather than a fixed pause.
        device.wait(
            Until.hasObject(By.clazz(RESULT_ROW).textContains(title)),
            ARRIVAL_TIMEOUT_MILLIS,
        )
    }

    /** What is written on screen, for a failure that has to say where it got to. */
    private fun UiDevice.visibleText(): String =
        findObjects(By.textStartsWith("")).mapNotNull { it.text }.filter { it.isNotBlank() }.take(
            VISIBLE_TEXT_SAMPLE,
        ).joinToString(" | ")

    private companion object {
        const val PACKAGE = "io.lukia"

        const val LOG_TAG = "ReaderOpenBenchmark"

        const val SEARCH_TAB = "Search"

        /** What a page of an open book calls itself - see `PageSheet` and `PageList`. */
        val PAGE_LABEL: Pattern = Pattern.compile("Page \\d+ of \\d+")

        const val TEXT_FIELD = "android.widget.EditText"

        /** A row of the results, which is everything the field is not. */
        const val RESULT_ROW = "android.widget.TextView"

        const val ITERATIONS = 5

        /** Enough of the screen to recognise it by, and not so much as to fill the log. */
        const val VISIBLE_TEXT_SAMPLE = 25

        /** A stale row is worth reaching for again; a missing one is not. */
        const val ROW_ATTEMPTS = 3

        /** Deep enough to leave a book, shallow enough not to leave the app. */
        const val BACK_ATTEMPTS = 3

        const val ARRIVAL_TIMEOUT_MILLIS = 10_000L

        /** A long book laid out from end to end is the thing being measured. */
        const val OPENING_TIMEOUT_MILLIS = 120_000L
    }
}
