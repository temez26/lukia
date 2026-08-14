package io.lukia.features.bookdetail.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.AppError
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.runCatchingApp
import io.lukia.core.domain.toAppError
import io.lukia.core.presentation.UiState
import io.lukia.core.presentation.dataOrNull
import io.lukia.features.bookdetail.application.GetDocumentDetailsUseCase
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
    private val removeDocumentUseCase: RemoveDocumentUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<DocumentInfo>>(UiState.Loading)
    val uiState: StateFlow<UiState<DocumentInfo>> = _uiState.asStateFlow()

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
                    onSuccess = { document ->
                        document
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
            setFavoriteUseCase(location, !current.isFavorite)
            load()
        }
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
