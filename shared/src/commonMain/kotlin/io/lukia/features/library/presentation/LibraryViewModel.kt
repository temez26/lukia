package io.lukia.features.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.features.import.domain.DocumentInfo
import io.lukia.features.import.domain.ImportDocumentUseCase
import io.lukia.features.import.domain.PickedDocument
import io.lukia.features.library.domain.GetLibraryDocumentsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val getLibraryDocumentsUseCase: GetLibraryDocumentsUseCase,
    private val importDocumentUseCase: ImportDocumentUseCase
) : ViewModel() {

    private val _documents = MutableStateFlow<List<DocumentInfo>>(emptyList())
    val documents: StateFlow<List<DocumentInfo>> = _documents.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadDocuments()
    }

    private fun loadDocuments() {
        viewModelScope.launch {
            _documents.value = getLibraryDocumentsUseCase()
        }
    }


    fun importDocument(pickedDocument: PickedDocument) {
        viewModelScope.launch {
            val result = importDocumentUseCase.execute(pickedDocument)

            result.onSuccess {
                loadDocuments()
                _errorMessage.value = null
            }.onFailure {
                _errorMessage.value = it.message ?: "An Error Occurred"
            }
        }
    }
}