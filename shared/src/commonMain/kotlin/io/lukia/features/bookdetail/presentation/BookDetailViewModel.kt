package io.lukia.features.bookdetail.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.features.bookdetail.application.GetDocumentDetailsUseCase
import io.lukia.core.domain.DocumentInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BookDetailViewModel(
    private val location: String,
    private val getDocumentDetailsUseCase: GetDocumentDetailsUseCase
) : ViewModel() {

    private val _document = MutableStateFlow<DocumentInfo?>(null)
    val document: StateFlow<DocumentInfo?> = _document.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            _document.value = getDocumentDetailsUseCase(location)
            _isLoading.value = false
        }
    }
}
