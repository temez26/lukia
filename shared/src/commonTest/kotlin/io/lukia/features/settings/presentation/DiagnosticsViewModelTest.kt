package io.lukia.features.settings.presentation

import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.FakeCrashStore
import io.lukia.core.domain.diagnostics.warn
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.core.infrastructure.diagnostics.CrashRecorder
import io.lukia.core.infrastructure.diagnostics.DiagnosticBuffer
import io.lukia.core.infrastructure.diagnostics.RecordingDiagnostics
import io.lukia.features.settings.infrastructure.AppSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The graph as the app builds it: one settings repository, shared.
 *
 * It has to be one. A repository reads the store once into a flow, so a second
 * over the same store answers with whatever was written when it was built - and
 * a recorder holding that second one would go on saying "off" after the reader
 * had turned it on. That is why the graph registers it as a `single`.
 */
private class Diagnostics {
    val store = FakeSettingsStore()
    val buffer = DiagnosticBuffer()
    val settings = AppSettingsRepository(store)
    val crashes = CrashRecorder(store = FakeCrashStore(), buffer = buffer, platformName = "Java 21")
    val viewModel = DiagnosticsViewModel(
        buffer = buffer,
        settings = settings,
        crashes = crashes,
    )

    val recorder = RecordingDiagnostics(
        buffer = buffer,
        enabled = { settings.settings.value.diagnostics },
    )
}

class DiagnosticsViewModelTest {

    @Test
    fun nothingIsRecordedUntilTheReaderAsksForIt() {
        val app = Diagnostics()

        app.recorder.warn(DiagnosticArea.Import, "cover-extraction-failed")

        assertEquals(emptyList(), app.buffer.entries.value)

        app.viewModel.setEnabled(true)
        app.recorder.warn(DiagnosticArea.Import, "cover-extraction-failed")

        assertEquals(1, app.buffer.entries.value.size)
    }

    /**
     * The setting is the reader's answer to whether the app holds a record of
     * them at all. Leaving what was collected in memory would quietly make
     * "off" mean "stopped adding to it".
     */
    @Test
    fun turningItOffThrowsAwayWhatWasCollected() {
        val app = Diagnostics()

        app.viewModel.setEnabled(true)
        app.recorder.warn(DiagnosticArea.Reading, "pdf-pages-unreadable", detail = "12 of 412")

        assertTrue(app.buffer.entries.value.isNotEmpty())

        app.viewModel.setEnabled(false)

        assertEquals(emptyList(), app.buffer.entries.value)
    }

    @Test
    fun theSettingSurvivesTheAppBeingClosed() {
        val app = Diagnostics()

        app.viewModel.setEnabled(true)

        assertTrue(AppSettingsRepository(app.store).settings.value.diagnostics)
    }

    @Test
    fun diagnosticsAreOffForSomebodyWhoHasNeverChosen() {
        assertFalse(Diagnostics().viewModel.appSettings.value.diagnostics)
    }

    @Test
    fun clearingLeavesTheSettingAlone() {
        val app = Diagnostics()

        app.viewModel.setEnabled(true)
        app.recorder.warn(DiagnosticArea.Storage, "schema-migrated", detail = "8 -> 9")

        app.viewModel.clear()

        assertEquals(emptyList(), app.buffer.entries.value)
        assertTrue(app.viewModel.appSettings.value.diagnostics, "clearing turned recording off")
    }

    @Test
    fun theReportCarriesWhatWasRecorded() {
        val app = Diagnostics()

        app.viewModel.setEnabled(true)
        app.recorder.warn(DiagnosticArea.Import, "cover-extraction-failed", detail = "EPUB")

        val report = app.viewModel.report()

        assertTrue(report.contains("cover-extraction-failed"), report)
        assertTrue(report.contains("EPUB"), report)
    }
}
