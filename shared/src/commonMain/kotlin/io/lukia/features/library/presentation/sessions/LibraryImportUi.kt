package io.lukia.features.library.presentation.sessions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.library.presentation.model.ImportProgress

/**
 * An import as a screen deals with it: whether one is running, how far through
 * it is, and the way to stop it.
 *
 * Three values that always arrive together and are only ever read together,
 * gathered for the reason [LibraryShelfUi] gathers a dozen - a screen's
 * signature should stay about the screen. The third of them is why this exists
 * now rather than earlier: two of these were already threaded through four
 * composables, and a third travelling beside them is the point at which they
 * stop being parameters and start being a thing.
 */
@Stable
class LibraryImportUi(
    val isImporting: Boolean = false,
    /**
     * How far through, where there is a count to give - which is a batch of more
     * than one. Null for a single file, whose import is over before "1 of 1"
     * could be read.
     */
    val progress: ImportProgress? = null,
    /** Stops the import, keeping the books that already arrived. */
    val onCancel: () -> Unit = {},
) {
    companion object {
        val None = LibraryImportUi()
    }
}

/**
 * The running import and the way to stop it, in one value.
 *
 * Remembered against what actually changes, so a shelf being scrolled does not
 * hand the header a fresh set of callbacks - the same bargain
 * [rememberShelfUi][io.lukia.features.library.presentation.screens.rememberShelfUi]
 * makes.
 */
@Composable
internal fun rememberImportUi(viewModel: LibraryViewModel): LibraryImportUi {
    val isImporting by viewModel.isImporting.collectAsState()
    val progress by viewModel.importProgress.collectAsState()

    return remember(viewModel, isImporting, progress) {
        LibraryImportUi(
            isImporting = isImporting,
            progress = progress,
            onCancel = viewModel::cancelImport,
        )
    }
}
