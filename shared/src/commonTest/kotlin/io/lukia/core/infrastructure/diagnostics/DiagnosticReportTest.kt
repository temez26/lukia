package io.lukia.core.infrastructure.diagnostics

import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.DiagnosticEntry
import io.lukia.core.domain.diagnostics.DiagnosticLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class DiagnosticReportTest {

    @Test
    fun anEntryReadsAsOneLineOfItsOwnColumns() {
        val line = entry(
            event = "cover-extraction-failed",
            detail = "EPUB",
            causeType = "ZipException",
        ).asReportLine()

        assertEquals(1, line.lines().size, line)
        assertTrue(line.contains("WARNING"), line)
        assertTrue(line.contains("Import"), line)
        assertTrue(line.contains("cover-extraction-failed"), line)
        assertTrue(line.contains("EPUB"), line)
        assertTrue(line.contains("ZipException"), line)
    }

    @Test
    fun anEntryWithoutADetailOrACauseStillReadsAsALine() {
        val line = entry(event = "reflow-cache-write-failed").asReportLine()

        assertEquals(1, line.lines().size, line)
        assertTrue(line.endsWith("reflow-cache-write-failed"), line)
    }

    /**
     * Which of the three builds produced a report is half of what makes one
     * actionable, and it is the only thing in a report that is not an entry.
     */
    @Test
    fun theHeaderNamesThePlatformAndCountsWhatFollows() {
        val report = diagnosticReport(
            entries = listOf(entry(event = "one"), entry(event = "two")),
            platformName = "Android 34",
        )

        val header = report.lines().first()

        assertTrue(header.contains("Android 34"), header)
        assertTrue(header.contains("2 entries"), header)
    }

    @Test
    fun oneEntryIsCountedInTheSingular() {
        val report = diagnosticReport(listOf(entry(event = "one")), platformName = "Java 21")

        assertTrue(report.lines().first().endsWith("1 entry"), report)
    }

    /**
     * An empty record has to say so. Handing somebody a header and nothing else
     * reads as a report that failed to save rather than as a run with nothing
     * wrong in it.
     */
    @Test
    fun anEmptyRecordSaysThatNothingWasRecorded() {
        val report = diagnosticReport(entries = emptyList(), platformName = "iOS 17.2")

        assertTrue(report.contains("Nothing was recorded."), report)
        assertTrue(report.lines().first().contains("0 entries"), report)
    }

    /**
     * The promise travels with the record, for the person receiving it as much
     * as for the one handing it over: it says which questions not to ask of it.
     */
    @Test
    fun everyReportCarriesWhatItPromisesAboutItself() {
        val report = diagnosticReport(listOf(entry(event = "one")), platformName = "Java 21")

        assertTrue(report.contains("no book text"), report)
    }

    @Test
    fun everyEntryReachesTheReport() {
        val entries = (1..50).map { entry(event = "event-$it") }

        val report = diagnosticReport(entries, platformName = "Java 21")

        entries.forEach { assertTrue(report.contains(it.event), "${it.event} is missing") }
    }

    private fun entry(
        event: String,
        detail: String? = null,
        causeType: String? = null,
    ) = DiagnosticEntry(
        at = Instant.fromEpochMilliseconds(0),
        level = DiagnosticLevel.Warning,
        area = DiagnosticArea.Import,
        event = event,
        detail = detail,
        causeType = causeType,
    )
}
