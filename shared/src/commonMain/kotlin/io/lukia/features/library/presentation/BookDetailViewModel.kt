package io.lukia.features.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.core.domain.document.ServerBookDownloader
import io.lukia.core.domain.document.ServerBookRemover
import io.lukia.core.domain.document.ServerBookUploader
import io.lukia.core.domain.document.ShelfChanges
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.library.application.BookDetails
import io.lukia.features.library.application.GetDocumentDetailsUseCase
import io.lukia.features.library.application.RefreshDocumentUseCase
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.library.application.SetFavoriteUseCase
import io.lukia.features.library.presentation.model.LibraryMessage
import io.lukia.features.library.presentation.screens.BookTransfer
import io.lukia.features.library.presentation.sessions.BookTransfers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BookDetailViewModel(
    private val location: String,
    private val getDocumentDetailsUseCase: GetDocumentDetailsUseCase,
    private val setFavoriteUseCase: SetFavoriteUseCase,
    private val refreshDocumentUseCase: RefreshDocumentUseCase,
    private val removeDocumentUseCase: RemoveDocumentUseCase,
    /** Fetches a server book onto the device. Defaulted for the screens and tests that never meet one. */
    private val downloader: ServerBookDownloader = ServerBookDownloader { _, _ ->
        throw AppErrorException(AppError.DocumentNotFound)
    },
    shelfChanges: ShelfChanges = ShelfChanges(),
    /** Sends a book on this device to the server. Defaulted for the same reason as [downloader]. */
    private val uploader: ServerBookUploader = ServerBookUploader.None,
    /** Takes a server book out of the shared library, for the admin. Defaulted for the same reason. */
    remover: ServerBookRemover = ServerBookRemover.None,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<BookDetails>>(UiState.Loading)
    val uiState: StateFlow<UiState<BookDetails>> = _uiState.asStateFlow()

    /**
     * Transient notice for the snackbar. Kept apart from [uiState] for the same
     * reason the library keeps its own apart: a re-reading that failed must not
     * take a book off the screen that is still perfectly readable.
     */
    private val _message = MutableStateFlow<LibraryMessage?>(null)
    val message: StateFlow<LibraryMessage?> = _message.asStateFlow()

    private val transfers = BookTransfers(downloader, uploader, viewModelScope, remover)

    /** How far moving this book to or from the server has got - see [BookTransfers]. */
    val transfer: StateFlow<BookTransfer> = transfers.stateOf(location)

    /** Whether a book here could be added to the server: there is one, and the reader is signed in to it. */
    val canUpload: StateFlow<Boolean> = transfers.canUpload

    /** Whether this book can be taken out of the shared library from here: true for the server's admin. */
    val canRemoveFromServer: StateFlow<Boolean> = transfers.canRemove

    init {
        load()
        // A sync can change this book - its place, its favourite - while it is on screen.
        viewModelScope.launch { shelfChanges.changes.collect { load() } }
    }

    /** Reads the book again on coming back to it, from the reader or from another app. */
    fun reload() {
        if (_uiState.value !is UiState.Loading) load()
    }

    /** Fetches a server book onto the device, or sends one on the device to the server - whichever it lacks. */
    fun startTransfer() {
        val document = (_uiState.value as? UiState.Success)?.data?.document ?: return
        transfers.start(document, onDone = ::load)
    }

    fun cancelTransfer() = transfers.cancel(location)

    /**
     * A location with no entry behind it is a failure rather than empty content:
     * this screen exists to show one book, so without it there is nothing here.
     */
    private fun load() {
        viewModelScope.launch {
            _uiState.value = runCatchingApp { getDocumentDetailsUseCase(location) }
                .fold(
                    onSuccess = { details ->
                        details
                            ?.let { UiState.Success(it) }
                            ?: UiState.Failure(AppError.DocumentNotFound)
                    },
                    onFailure = { UiState.Failure(it.toAppError()) },
                )
        }
    }

    fun retry() {
        _uiState.value = UiState.Loading
        load()
    }

    /**
     * Deriving the new value from the loaded document keeps the toggle decision
     * out of the composable, matching how the library grid does it.
     */
    fun toggleFavorite() {
        val current = _uiState.value.dataOrNull ?: return

        viewModelScope.launch {
            setFavoriteUseCase(location, !current.document.isFavorite)
            load()
        }
    }

    /**
     * Reads the book's file again, for a cover or a title that came in wrong.
     *
     * Reloads rather than patching what is on screen: this screen shows one
     * book and nothing else, so re-reading its row costs nothing worth saving.
     */
    fun refreshDocument() {
        viewModelScope.launch {
            runCatchingApp { refreshDocumentUseCase(location).getOrThrow() }
                .onFailure { _message.value = LibraryMessage.Failed(it.toAppError()) }
                .onSuccess {
                    _message.value = LibraryMessage.DetailsRefreshed
                    load()
                }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    /**
     * Removes the book from the library index only; the file is left on disk.
     *
     * The screen navigates away rather than reloading - there is nothing left
     * here to show once the entry is gone.
     */

    /**
     * [onGone] when the book has left this shelf with it - one never downloaded
     * has nothing left here to show. A downloaded copy stays, and so does the page.
     */
    fun removeFromServer(onGone: () -> Unit) {
        val wasOnlyOnServer = _uiState.value.dataOrNull?.document?.awaitsDownload == true

        viewModelScope.launch {
            runCatchingApp { transfers.remove(location) }
                .onSuccess { if (wasOnlyOnServer) onGone() }
                .onFailure { _message.value = LibraryMessage.Failed(it.toAppError()) }
        }
    }

    fun deleteDocument() {
        viewModelScope.launch {
            removeDocumentUseCase(location)
        }
    }
}
