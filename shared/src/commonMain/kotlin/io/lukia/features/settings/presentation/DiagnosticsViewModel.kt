package io.lukia.features.settings.presentation

import androidx.lifecycle.ViewModel
import io.lukia.core.domain.diagnostics.DiagnosticEntry
import io.lukia.core.infrastructure.diagnostics.CrashRecorder
import io.lukia.core.infrastructure.diagnostics.DiagnosticBuffer
import io.lukia.core.infrastructure.diagnostics.diagnosticReport
import io.lukia.features.settings.domain.AppSettings
import io.lukia.features.settings.infrastructure.AppSettingsRepository
import io.lukia.getPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The diagnostics record, and the one setting that decides whether there is one.
 *
 * Its own view model rather than more of [SettingsViewModel]: nothing here has
 * anything to do with the four groups of settings, and that file is already at
 * the length where whatever went into it next would have to come back out.
 */
class DiagnosticsViewModel(
    private val buffer: DiagnosticBuffer,
    private val settings: AppSettingsRepository,
    private val crashes: CrashRecorder,
) : ViewModel() {

    /**
     * Read once, when the settings screen is opened rather than when the app
     * starts: it is one small file and nobody is waiting on it, and reading it
     * any earlier would be work done on every launch for a screen most readers
     * never open.
     */
    private val _lastCrash = MutableStateFlow(crashes.lastCrash())

    /** What the last death left behind, or null when the app closed properly. */
    val lastCrash: StateFlow<String?> = _lastCrash.asStateFlow()

    val entries: StateFlow<List<DiagnosticEntry>> = buffer.entries

    /**
     * The whole of the app's settings rather than the one flag read out of it,
     * so this holds no second copy of a value that already lives in one place.
     */
    val appSettings: StateFlow<AppSettings> = settings.settings

    fun setEnabled(on: Boolean) {
        settings.update(settings.settings.value.copy(diagnostics = on))

        // Turning it off throws away what was collected while it was on. This
        // setting is the reader's answer to whether the app keeps a record of
        // them at all, and leaving the old one in memory would quietly make
        // "off" mean "stopped adding to it".
        if (!on) buffer.clear()
    }

    fun clear() = buffer.clear()

    /** Deletes the crash file. The reader is the only one who can. */
    fun discardCrash() {
        crashes.discard()
        _lastCrash.value = null
    }

    /** The whole record as text, which is exactly what the reader copies. */
    fun report(): String = diagnosticReport(
        entries = entries.value,
        platformName = getPlatform().name,
    )
}
