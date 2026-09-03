package io.lukia.features.settings.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import io.lukia.features.settings.presentation.screens.DiagnosticsSettings
import org.koin.compose.viewmodel.koinViewModel

/**
 * The diagnostics group with its own state behind it, ready to be dropped into
 * whichever way Settings is being shown.
 *
 * Kept apart from the group itself so that `DiagnosticsSettings` stays a
 * composable that takes values and reports changes - which is what lets the
 * settings body be composed in a test with no dependency graph behind it.
 *
 * The view model is resolved here and nowhere higher, so a caller that never
 * shows this never asks the graph for one.
 */
@Composable
internal fun DiagnosticsGroup(
    groupColor: Color = MaterialTheme.colorScheme.surface,
    viewModel: DiagnosticsViewModel = koinViewModel(),
) {
    val settings by viewModel.appSettings.collectAsState()
    val entries by viewModel.entries.collectAsState()
    val lastCrash by viewModel.lastCrash.collectAsState()

    // The clipboard is the whole of the export, and deliberately so. The app has
    // no server to send a report to, and giving it one would be a larger change
    // than everything this record is for - so the reader copies it and chooses
    // themselves what to paste it into. Nothing here can reach the network.
    val clipboard = LocalClipboardManager.current

    DiagnosticsSettings(
        enabled = settings.diagnostics,
        entries = entries,
        onEnabledChange = viewModel::setEnabled,
        onCopy = { clipboard.setText(AnnotatedString(viewModel.report())) },
        onClear = viewModel::clear,
        groupColor = groupColor,
        lastCrash = lastCrash,
        onCopyCrash = { lastCrash?.let { clipboard.setText(AnnotatedString(it)) } },
        onDiscardCrash = viewModel::discardCrash,
    )
}
