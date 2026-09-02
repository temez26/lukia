package io.lukia.core.infrastructure.diagnostics

import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.DiagnosticLevel
import io.lukia.core.domain.diagnostics.failure
import io.lukia.core.domain.diagnostics.info
import io.lukia.core.domain.diagnostics.warn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class RecordingDiagnosticsTest {

    @Test
    fun eachHelperRecordsItsOwnLevel() {
        val buffer = DiagnosticBuffer()
        val diagnostics = recording(buffer)

        diagnostics.info(DiagnosticArea.Import, "started")
        diagnostics.warn(DiagnosticArea.Reading, "degraded")
        diagnostics.failure(DiagnosticArea.Translation, "failed")

        assertEquals(
            listOf(DiagnosticLevel.Info, DiagnosticLevel.Warning, DiagnosticLevel.Failure),
            buffer.entries.value.map { it.level },
        )
    }

    @Test
    fun nothingIsRecordedWhileDiagnosticsAreOff() {
        val buffer = DiagnosticBuffer()
        val diagnostics = recording(buffer, enabled = false)

        diagnostics.warn(DiagnosticArea.Import, "cover-extraction-failed", detail = "EPUB")

        assertEquals(emptyList(), buffer.entries.value)
    }

    /**
     * The single largest way something about the reader could get into a record.
     * A throwable's message is where Ktor puts the request URL and where a file
     * failure puts the path, and a path on the desktop names the person whose
     * folder it is in.
     */
    @Test
    fun aThrowableIsKeptByItsTypeAndNeverByItsMessage() {
        val buffer = DiagnosticBuffer()
        val diagnostics = recording(buffer)

        diagnostics.warn(
            area = DiagnosticArea.Import,
            event = "cover-extraction-failed",
            cause = IllegalStateException("/Users/anna-liisa/Books/Anna-Liisa.epub is unreadable"),
        )

        val entry = buffer.entries.value.single()

        assertEquals("IllegalStateException", entry.causeType)
        assertNull(entry.detail)
        assertTrue(
            buffer.entries.value.none { it.toString().contains("anna-liisa") },
            "the throwable's message reached the record",
        )
    }

    @Test
    fun anEntryIsStampedWhenItWasRecorded() {
        val buffer = DiagnosticBuffer()
        var millis = 0L
        val diagnostics = RecordingDiagnostics(
            buffer = buffer,
            now = { Instant.fromEpochMilliseconds(millis) },
        )

        diagnostics.info(DiagnosticArea.Storage, "schema-created")
        millis = 5_000
        diagnostics.info(DiagnosticArea.Storage, "schema-migrated")

        assertEquals(
            listOf(0L, 5_000L),
            buffer.entries.value.map { it.at.toEpochMilliseconds() },
        )
    }

    private fun recording(buffer: DiagnosticBuffer, enabled: Boolean = true) =
        RecordingDiagnostics(
            buffer = buffer,
            enabled = { enabled },
            now = { Instant.fromEpochMilliseconds(0) },
        )
}
