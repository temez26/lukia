package io.lukia.features.library.presentation

import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.core.presentation.displayShortMessage
import io.lukia.features.library.application.ImportDocumentsUseCase
import io.lukia.features.library.application.ImportFolderUseCase
import io.lukia.features.library.application.ImportSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Getting books into the library, and saying how it is going.
 *
 * Held apart from [LibraryViewModel] for the same reason [LibrarySelectionSession]
 * is: importing has its own state, its own idea of being busy and its own way of
 * reporting, and none of it is about the shelf the view model otherwise keeps.
 *
 * [onImported] is what the library does about a finished import - reload - which
 * belongs to whoever owns the library rather than to this.
 */
class LibraryImportSession(
    private val importDocumentsUseCase: ImportDocumentsUseCase,
    private val importFolderUseCase: ImportFolderUseCase,
    private val scope: CoroutineScope,
    private val onImported: () -> Unit,
    private val onMessage: (String?) -> Unit,
) {

    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    /**
     * How far through the current import the library is, or null when there is
     * no count to give - a single file, or an import that has not started.
     *
     * Separate from [isImporting] because a folder can hold hundreds of books
     * and each one is opened and has its cover rendered: without a count, a scan
     * of a large shelf is several minutes of a spinner that says nothing about
     * whether it is working or stuck.
     */
    private val _progress = MutableStateFlow<ImportProgress?>(null)
    val progress: StateFlow<ImportProgress?> = _progress.asStateFlow()

    fun importDocuments(pickedDocuments: List<PickedDocument>) {
        if (pickedDocuments.isEmpty()) return

        run { importDocumentsUseCase(pickedDocuments, ::report) }
    }

    fun importFolder(pickedFolder: PickedFolder) {
        run { importFolderUseCase(pickedFolder, ::report) }
    }

    /**
     * Only counted for a batch worth counting. One file is done before a
     * "1 of 1" could be read, and a number that appears and vanishes reads as a
     * flicker rather than as progress.
     */
    private fun report(completed: Int, total: Int) {
        _progress.value = if (total > 1) ImportProgress(completed, total) else null
    }

    /**
     * The one place an import's outcome is handled: holds [isImporting] for the
     * duration, reloads on success, and reports any failure - named or
     * unforeseen - the same way.
     */
    private fun run(block: suspend () -> Result<ImportSummary>) {
        scope.launch {
            _isImporting.value = true
            onMessage(null)

            try {
                // getOrThrow re-raises the use case's own failure so that it and
                // an unanticipated throw are both narrowed in the same place.
                runCatchingApp { block().getOrThrow() }
                    .onFailure { onMessage(it.toAppError().displayShortMessage()) }
                    .onSuccess { summary ->
                        // A partly imported batch is not a failure - the books
                        // that did come in are usable - so it reports itself as
                        // a notice rather than taking the screen over.
                        onMessage(summary.notice())
                        onImported()
                    }
            } finally {
                // In a finally so nothing can leave the library spinning.
                _isImporting.value = false
                _progress.value = null
            }
        }
    }
}
