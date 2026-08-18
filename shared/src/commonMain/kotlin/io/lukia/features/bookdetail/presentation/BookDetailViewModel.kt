package io.lukia.features.bookdetail.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.AppError
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.core.presentation.displayShortMessage
import io.lukia.features.bookdetail.application.BookDetails
import io.lukia.features.bookdetail.application.GetDocumentDetailsUseCase
import io.lukia.features.library.application.RefreshDocumentUseCase
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.library.application.SetFavoriteUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BookDetailViewModel(
    private val location: String,
    private val getDocumentDetailsUseCase: GetDocumentDetailsUseCase,
    private val setFavoriteUseCase: SetFavoriteUseCase,
    private val refreshDocumentUseCase: RefreshDocumentUseCase,
    private val removeDocumentUseCase: RemoveDocumentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<BookDetails>>(UiState.Loading)
    val uiState: StateFlow<UiState<BookDetails>> = _uiState.asStateFlow()

    /**
     * Transient notice for the snackbar. Kept apart from [uiState] for the same
     * reason the library keeps its own apart: a re-reading that failed must not
     * take a book off the screen that is still perfectly readable.
     */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        load()
    }

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
                    onFailure = { UiState.Failure(it.toAppError()) }
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
                .onFailure { _message.value = it.toAppError().displayShortMessage() }
                .onSuccess {
                    _message.value = "Details refreshed"
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
    fun deleteDocument() {
        viewModelScope.launch {
            removeDocumentUseCase(location)
        }
    }
}
