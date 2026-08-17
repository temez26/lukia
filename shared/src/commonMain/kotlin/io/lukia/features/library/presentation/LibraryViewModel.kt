package io.lukia.features.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.displayShortMessage
import io.lukia.features.library.application.GetLibraryDocumentsUseCase
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.features.library.application.ImportFolderUseCase
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.library.application.SetFavoriteUseCase
import io.lukia.features.reader.domain.ReadingProgress
import io.lukia.features.reader.domain.ReadingProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val getLibraryDocumentsUseCase: GetLibraryDocumentsUseCase,
    private val importDocumentUseCase: ImportDocumentUseCase,
    private val importFolderUseCase: ImportFolderUseCase,
    private val setFavoriteUseCase: SetFavoriteUseCase,
    private val removeDocumentUseCase: RemoveDocumentUseCase,
    private val readingProgressRepository: ReadingProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<DocumentInfo>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<DocumentInfo>>> = _uiState.asStateFlow()

    /**
     * How far through each book its reader is, for the bars the grid draws on
     * its covers. Keyed by location, and holding only the books that have
     * actually been opened.
     *
     * Kept apart from [uiState] rather than folded into each [DocumentInfo]:
     * this is read from a different store, changes for a different reason -
     * every time a book is read - and a failure to read it must not be able to
     * take the library off the screen. A grid with no bars is a small loss; a
     * grid with no books is the whole app.
     */
    private val _readingProgress = MutableStateFlow<Map<String, ReadingProgress>>(emptyMap())
    val readingProgress: StateFlow<Map<String, ReadingProgress>> = _readingProgress.asStateFlow()

    /**
     * Transient notice for the snackbar - a failed import, a folder that came in
     * partly. Kept apart from [uiState] on purpose: none of these should blank
     * out a library the user can still read and use.
     */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /**
     * True while an import is running. Reading the library is covered by
     * [uiState]; this is the slow work that happens on top of a library that is
     * already on screen.
     */
    private val _isImporting = MutableStateFlow(false)
    val isImporting: StateFlow<Boolean> = _isImporting.asStateFlow()

    init {
        loadDocuments()
    }

    /**
     * Re-reads the library. Needed because the detail screen can favourite or
     * remove a book while this ViewModel is alive but off screen - it is scoped
     * to a back stack entry that survives the trip - so returning to the grid
     * would otherwise show the state from before.
     */
    fun refresh() {
        loadDocuments()
    }

    private fun loadDocuments() {
        viewModelScope.launch {
            _uiState.value = runCatchingApp { getLibraryDocumentsUseCase() }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Failure(it.toAppError()) }
                )

            // After the library and never instead of it: the books are what the
            // screen is for, and the bars on them are a decoration on top. A
            // store that cannot be read leaves the grid without bars rather
            // than without books.
            _readingProgress.value = runCatching { readingProgressRepository.allProgress() }
                .getOrDefault(emptyMap())
        }
    }

    fun importDocument(pickedDocument: PickedDocument) {
        runImport { importDocumentUseCase(pickedDocument).map { } }
    }

    fun importFolder(pickedFolder: PickedFolder) {
        runImport {
            importFolderUseCase(pickedFolder).map { summary ->
                // A partly imported folder is not a failure - the books that did
                // come in are usable - so it reports itself as a notice rather
                // than taking the screen over.
                if (summary.failed > 0) {
                    _message.value = "Imported ${summary.imported}, skipped ${summary.failed}"
                }
            }
        }
    }

    /**
     * The one place an import's outcome is handled: holds [isImporting] for the
     * duration, reloads on success, and reports any failure - named or
     * unforeseen - through the snackbar.
     */
    private fun runImport(block: suspend () -> Result<Unit>) {
        viewModelScope.launch {
            _isImporting.value = true
            _message.value = null

            try {
                // getOrThrow re-raises the use case's own failure so that it and
                // an unanticipated throw are both narrowed in the same place.
                runCatchingApp { block().getOrThrow() }
                    .onFailure { _message.value = it.toAppError().displayShortMessage() }
                    .onSuccess { loadDocuments() }
            } finally {
                // In a finally so nothing can leave the library spinning.
                _isImporting.value = false
            }
        }
    }

    /**
     * Deriving the new value from [document] keeps the toggle decision out of
     * the composable.
     */
    fun toggleFavorite(document: DocumentInfo) {
        viewModelScope.launch {
            setFavoriteUseCase(document.document.location, !document.isFavorite)
            loadDocuments()
        }
    }

    /** Removes the book from the library index only; the file is left on disk. */
    fun deleteDocument(document: DocumentInfo) {
        viewModelScope.launch {
            removeDocumentUseCase(document.document.location)
            loadDocuments()
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
