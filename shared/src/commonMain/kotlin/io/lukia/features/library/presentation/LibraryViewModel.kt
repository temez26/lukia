package io.lukia.features.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.core.presentation.displayShortMessage
import io.lukia.features.library.application.GetLibraryDocumentsUseCase
import io.lukia.features.library.application.ImportDocumentsUseCase
import io.lukia.features.library.application.ImportFolderUseCase
import io.lukia.features.library.application.RefreshDocumentUseCase
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.library.application.SetFavoriteUseCase
import io.lukia.features.library.infrastructure.LibrarySettingsRepository
import io.lukia.features.reader.domain.ReadingProgress
import io.lukia.features.reader.domain.ReadingProgressRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(
    private val getLibraryDocumentsUseCase: GetLibraryDocumentsUseCase,
    importDocumentsUseCase: ImportDocumentsUseCase,
    importFolderUseCase: ImportFolderUseCase,
    private val setFavoriteUseCase: SetFavoriteUseCase,
    private val refreshDocumentUseCase: RefreshDocumentUseCase,
    private val removeDocumentUseCase: RemoveDocumentUseCase,
    private val readingProgressRepository: ReadingProgressRepository,
    private val librarySettingsRepository: LibrarySettingsRepository,
    /**
     * Where the library is narrowed, ordered and measured. Off the main thread
     * in production, and injectable so tests can run it deterministically.
     */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    /**
     * Selecting several books at once. Held apart rather than added to this -
     * see [LibrarySelectionSession] - so that a mode with its own state and its
     * own actions is a thing rather than a scattering of fields.
     */
    private val selectionSession = LibrarySelectionSession(
        setFavoriteUseCase = setFavoriteUseCase,
        refreshDocumentUseCase = refreshDocumentUseCase,
        removeDocumentUseCase = removeDocumentUseCase,
        progressRepository = readingProgressRepository,
        scope = viewModelScope,
    )

    val selection: StateFlow<LibrarySelection> = selectionSession.selection
    val bulkAction: StateFlow<BulkAction?> = selectionSession.running

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
     * Getting books in, and saying how it is going. Held apart - see
     * [LibraryImportSession] - so that importing is a thing with its own state
     * rather than four more fields on the shelf.
     */
    private val importSession = LibraryImportSession(
        importDocumentsUseCase = importDocumentsUseCase,
        importFolderUseCase = importFolderUseCase,
        scope = viewModelScope,
        onImported = ::loadDocuments,
        onMessage = { _message.value = it },
    )

    val isImporting: StateFlow<Boolean> = importSession.isImporting
    val importProgress: StateFlow<ImportProgress?> = importSession.progress

    /**
     * What the reader has asked of their library - the shelf, the filters, the
     * search and the order.
     *
     * Held here rather than in the screen so that narrowing the library is not
     * work done on the thread that draws it, and so that leaving for a book and
     * coming back finds the shelf as it was left.
     */
    private val _query = MutableStateFlow(LibraryQuery())
    val query: StateFlow<LibraryQuery> = _query.asStateFlow()

    /**
     * The whole library with its reading positions joined on and its search and
     * sort keys worked out - see [LibraryBook].
     *
     * Off the main thread and computed once per load rather than once per
     * keystroke, which is the whole point of it existing.
     */
    val library: StateFlow<List<LibraryBook>> =
        combine(_uiState, _readingProgress) { state, progress ->
            withContext(dispatcher) { state.dataOrNull.orEmpty().asLibrary(progress) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Looking for a book, which is a different question from narrowing the
     * shelf - see [LibrarySearchSession].
     */
    val search = LibrarySearchSession(
        library = library,
        scope = viewModelScope,
        dispatcher = dispatcher,
    )

    /** What this library can usefully be narrowed by, for the header's controls. */
    val facets: StateFlow<LibraryFacets> = library
        .map { books -> withContext(dispatcher) { books.facets() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LibraryFacets.Empty)

    /**
     * The books the grid draws: everything [query] asks for, in the order it
     * asks for.
     *
     * Every part of working this out - the filtering, the sorting, and the pass
     * that builds [library] behind it - is done on [dispatcher] and handed back
     * finished. The screen collects a list, so changing a filter costs it a
     * recomposition rather than a search through the whole library on the frame
     * the reader is watching.
     *
     * The work is moved with `withContext` inside each transform rather than
     * with `flowOn`, which would put a channel between the ViewModel and the
     * screen: the computing is what has to leave the main thread, not the
     * handing over of the answer.
     */
    val books: StateFlow<List<LibraryBook>> = combine(library, _query) { books, query ->
        withContext(dispatcher) { books.matching(query) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        loadDocuments()

        // A filter can lose its ground under the reader - the last favourite
        // un-favourited, the last EPUB deleted - and a query still asking for it
        // would empty the grid for a reason nothing on screen explains.
        viewModelScope.launch {
            facets.collect { facets -> _query.update { it.limitedTo(facets) } }
        }
    }

    /**
     * Exposed straight from the repository rather than copied here: it outlives
     * the screen, and mirroring it would be one more thing that could disagree
     * with what was stored.
     */
    val layout: StateFlow<LibraryLayout> = librarySettingsRepository.layout

    fun setLayout(layout: LibraryLayout) {
        librarySettingsRepository.setLayout(layout)
    }

    fun updateQuery(query: LibraryQuery) {
        _query.value = query
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
                    onFailure = { UiState.Failure(it.toAppError()) },
                )

            // After the library and never instead of it: the books are what the
            // screen is for, and the bars on them are a decoration on top. A
            // store that cannot be read leaves the grid without bars rather
            // than without books.
            _readingProgress.value = runCatching { readingProgressRepository.allProgress() }
                .getOrDefault(emptyMap())
        }
    }

    fun importDocuments(pickedDocuments: List<PickedDocument>) =
        importSession.importDocuments(pickedDocuments)

    fun importFolder(pickedFolder: PickedFolder) = importSession.importFolder(pickedFolder)

    /**
     * Deriving the new value from [document] keeps the toggle decision out of
     * the composable.
     */
    fun toggleFavorite(document: DocumentInfo) {
        val location = document.document.location
        val isFavorite = !document.isFavorite

        viewModelScope.launch {
            setFavoriteUseCase(location, isFavorite)

            // Patched in place rather than re-read. A reload would pull the
            // whole library back out of the database, and every row carries its
            // cover - megabytes of BLOB decoded and re-allocated to change one
            // flag on one book. What the write did is already known here, so
            // the row is edited to match it.
            updateLoadedDocuments { documents ->
                documents.map {
                    if (it.document.location == location) it.copy(isFavorite = isFavorite) else it
                }
            }
        }
    }

    /**
     * Reads one book's file again, for a cover or a title that came in wrong -
     * or a file that has been replaced on disk since it was imported.
     *
     * It says so when it worked, unlike an import: a re-reading that finds
     * exactly what was there before changes nothing on screen, and silence would
     * be indistinguishable from the action having done nothing at all.
     */
    fun refreshDocument(document: DocumentInfo) {
        val location = document.document.location

        viewModelScope.launch {
            runCatchingApp { refreshDocumentUseCase(location).getOrThrow() }
                .onFailure { _message.value = it.toAppError().displayShortMessage() }
                .onSuccess { refreshed ->
                    // Patched in place rather than re-read, for the reason
                    // favouriting is: one row changing is not a reason to pull
                    // every cover in the library back out of the database.
                    updateLoadedDocuments { documents ->
                        documents.map {
                            if (it.document.location == location) refreshed else it
                        }
                    }

                    _message.value = "Details refreshed"
                }
        }
    }

    /** Removes the book from the library index only; the file is left on disk. */
    fun deleteDocument(document: DocumentInfo) {
        val location = document.document.location

        viewModelScope.launch {
            removeDocumentUseCase(location)

            // Dropped from the list for the same reason favouriting patches it:
            // one row leaving is not a reason to re-read the rest.
            updateLoadedDocuments { documents ->
                documents.filterNot { it.document.location == location }
            }

            // The use case forgets the stored position too, so the bar has to go
            // with the book rather than linger until the next full read.
            _readingProgress.update { it - location }
        }
    }

    /**
     * Edits the library already on screen, when there is one.
     *
     * A library that failed to load or is still loading has nothing to edit, and
     * inventing a Success state from a local change would put books on screen
     * that were never read.
     */
    private fun updateLoadedDocuments(transform: (List<DocumentInfo>) -> List<DocumentInfo>) {
        _uiState.update { state ->
            if (state is UiState.Success) UiState.Success(transform(state.data)) else state
        }
    }

    fun startSelecting() = selectionSession.start()

    /**
     * What is worth tidying on the shelf as it is currently narrowed, so that
     * the menu can say how much there is before anyone presses anything.
     */
    val upkeep: StateFlow<LibraryUpkeep> = books
        .map { it.upkeep() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUpkeep.None)

    /**
     * Ticks the books whose files have gone, and leaves the reader looking at
     * them.
     *
     * Worked out from the shelf as it stands rather than read from [upkeep],
     * which is shared only while the menu showing its counts is on screen - an
     * action must act on the library, not on whether anybody was watching it.
     */
    fun selectMissing() = selectionSession.start(books.value.upkeep().missing)

    /** Ticks every copy but the first of each book that is on the shelf twice. */
    fun selectDuplicates() = selectionSession.start(books.value.upkeep().duplicates)

    fun stopSelecting() = selectionSession.stop()

    fun toggleSelected(location: String) = selectionSession.toggle(location)

    /**
     * Ticks what the shelf is showing rather than the whole library: a reader
     * who has narrowed to one author and pressed Select All has selected that
     * author, and being handed everything they own instead would be the app
     * answering a question nobody asked.
     */
    fun toggleAllSelected() = selectionSession.toggleAll(books.value.map { it.location })

    fun refreshSelected() = selectionSession.refresh(::onBulkDone)

    fun setFavoriteOnSelected() =
        selectionSession.setFavorite(_uiState.value.dataOrNull.orEmpty(), ::onBulkDone)

    fun resetProgressOnSelected() = selectionSession.resetProgress(::onBulkDone)

    fun deleteSelected() = selectionSession.delete(::onBulkDone)

    /**
     * The library is read again rather than patched after a bulk action.
     *
     * The single-book actions patch the loaded list, because one row changing is
     * not a reason to re-read the rest. A bulk action is the opposite case: it
     * may have touched every book on the shelf, and working out which of forty
     * patches to apply is more work - and more ways to be wrong - than reading
     * the shelf once.
     */
    private fun onBulkDone(message: String) {
        _message.value = message

        loadDocuments()
    }

    fun clearMessage() {
        _message.value = null
    }
}
