package io.lukia.features.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.features.import.domain.DocumentInfo
import io.lukia.features.import.domain.ImportDocumentUseCase
import io.lukia.features.import.infrastructure.ImportFilePicker
import io.lukia.features.library.domain.GetLibraryDocumentsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val getLibraryDocumentsUseCase: GetLibraryDocumentsUseCase,
    private val importFilePicker: ImportFilePicker,
    private val importDocumentUseCase: ImportDocumentUseCase
) : ViewModel() {

    private val _documents = MutableStateFlow<List<DocumentInfo>>(emptyList())
    val documents: StateFlow<List<DocumentInfo>> = _documents.asStateFlow()

    init {
        loadDocuments()
    }

    private fun loadDocuments() {
        viewModelScope.launch {
            _documents.value = getLibraryDocumentsUseCase()
        }
    }

    fun importDocument() {
        viewModelScope.launch {

            val pickedDocument = importFilePicker.pick() ?: return@launch

            importDocumentUseCase.execute(pickedDocument)

            loadDocuments()
        }
    }
}