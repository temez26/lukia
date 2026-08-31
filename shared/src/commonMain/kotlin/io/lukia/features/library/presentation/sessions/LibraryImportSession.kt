package io.lukia.features.library.presentation.sessions

import io.lukia.core.domain.document.IncomingDocument
import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.domain.document.PickedFolder
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.core.infrastructure.IncomingDocuments
import io.lukia.core.presentation.displayShortMessage
import io.lukia.features.library.application.ImportDocumentsUseCase
import io.lukia.features.library.application.ImportFolderUseCase
import io.lukia.features.library.application.ImportSummary
import io.lukia.features.library.presentation.model.ImportProgress
import io.lukia.features.library.presentation.model.notice
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
    /** Books other apps have handed to Lukia - see [IncomingDocuments]. */
    private val incoming: IncomingDocuments,
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
     * A book handed to the app by another one.
     *
     * Kept before it is imported, and that order is the whole of it: what
     * arrives is readable only while the grant that delivered it lasts, so the
     * copy has to be taken before anything is written to the shelf. Keeping is
     * inside the import rather than before it so that a document that cannot be
     * read reports itself the same way a book that cannot be analysed does,
     * instead of throwing out of the collector and stopping the inbox for good.
     *
     * Awaited rather than launched, so two books shared at once are imported one
     * after the other and the progress count means something.
     */
    private suspend fun importIncoming(document: IncomingDocument) {
        runImport {
            importDocumentsUseCase(listOf(incoming.store.keep(document)), ::report)
        }
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
        scope.launch { runImport(block) }
    }

    /** [run] without the coroutine, for a caller that is already in one. */
    private suspend fun runImport(block: suspend () -> Result<ImportSummary>) {
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

    /**
     * Starts taking books other apps have handed to Lukia.
     *
     * Last in the class, and that placement is load-bearing rather than tidy.
     * The collector runs eagerly on a main dispatcher the moment it is launched,
     * and a document already waiting in the inbox is taken on that first pass -
     * inside this object's own construction. Launched from an `init` at the top,
     * where it reads more naturally, that pass reaches [_isImporting] before the
     * property exists and dies on it, taking the inbox down with it for the rest
     * of the session and saying nothing.
     */
    init {
        // Collected for as long as the library lives, which on mobile is the
        // whole session: the three tabs share one of these - see `MobileShell` -
        // so a book shared into the app has somewhere to arrive whichever tab is
        // showing, and one queued during a cold start is taken as soon as there
        // is something to take it.
        scope.launch {
            incoming.inbox.documents.collect { document -> importIncoming(document) }
        }
    }
}
