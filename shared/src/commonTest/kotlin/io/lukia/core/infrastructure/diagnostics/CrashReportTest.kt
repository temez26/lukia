package io.lukia.core.infrastructure.diagnostics

import io.lukia.LukiaVersion
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class CrashReportTest {

    /**
     * A crash is read on a later run, possibly days later, so it carries a date
     * where the rest of the record carries only a time of day.
     */
    @Test
    fun theHeaderCarriesTheDayAsWellAsTheTime() {
        val report = report(IllegalStateException("boom"))

        assertTrue(report.lines().first().contains("1970-01-01"), report.lines().first())
        assertTrue(report.lines().first().contains("Java 21"), report.lines().first())
    }

    /**
     * A crash is read weeks later, against a build that has moved on. Without
     * the version there is no telling whether it is still worth chasing.
     */
    @Test
    fun theHeaderNamesTheVersionOfTheAppThatCrashed() {
        val report = crashReport(
            failure = IllegalStateException("boom"),
            entries = emptyList(),
            platformName = "Android 34",
            at = Instant.fromEpochMilliseconds(0),
            appVersion = "2.3.1",
        )

        assertTrue(report.lines().first().startsWith("Lukia 2.3.1 crash"), report.lines().first())
    }

    @Test
    fun theVersionDefaultsToTheOneTheAppWasBuiltAs() {
        assertTrue(
            report(IllegalStateException("boom")).lines().first().contains(LukiaVersion),
            report(IllegalStateException("boom")).lines().first(),
        )
    }

    @Test
    fun theFailureIsNamedByItsType() {
        assertTrue(report(IllegalStateException("boom")).contains("IllegalStateException"))
    }

    /**
     * The rule the whole record is built on, applied to the one place a message
     * would otherwise arrive whole: a path, a request URL, a reader's own name.
     */
    @Test
    fun theFailuresOwnWordsAreLeftOut() {
        val report = report(
            IllegalStateException("/Users/anna-liisa/Documents/Books/Anna-Liisa.epub is gone"),
        )

        assertFalse(report.contains("anna-liisa"), report)
        assertFalse(report.contains("Anna-Liisa"), report)
    }

    /** And left out of a cause too, which is where a wrapped failure hides one. */
    @Test
    fun aCauseIsNamedByItsTypeAndNotByItsWords() {
        val report = report(
            IllegalStateException("outer", IllegalArgumentException("/Users/anna-liisa/books")),
        )

        assertTrue(report.contains("caused by"), report)
        assertTrue(report.contains("IllegalArgumentException"), report)
        assertFalse(report.contains("anna-liisa"), report)
    }

    /**
     * Says so plainly rather than leaving a gap, and says what to do about it -
     * a reader reading this is exactly the person who could turn the run-up on
     * before it happens again.
     */
    @Test
    fun aCrashWithNoRunUpSaysThereWasNone() {
        assertTrue(report(IllegalStateException("boom")).contains("Nothing was recorded before it"))
    }

    @Test
    fun everyReportSaysWhyTheMessageIsMissing() {
        assertTrue(report(IllegalStateException("boom")).contains("Failure messages are left out"))
    }

    private fun report(failure: Throwable) = crashReport(
        failure = failure,
        entries = emptyList(),
        platformName = "Java 21",
        at = Instant.fromEpochMilliseconds(0),
    )
}
