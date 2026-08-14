package io.lukia.features.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.core.domain.DocumentInfo
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.core.domain.PickedDocument
import io.lukia.core.domain.PickedFolder
import io.lukia.features.library.application.GetLibraryDocumentsUseCase
import io.lukia.features.library.application.ImportFolderUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val getLibraryDocumentsUseCase: GetLibraryDocumentsUseCase,
    private val importDocumentUseCase: ImportDocumentUseCase,
    private val importFolderUseCase: ImportFolderUseCase
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
            val result = importDocumentUseCase(pickedDocument)

            result.onSuccess {
                loadDocuments()
                _errorMessage.value = null
            }.onFailure {
                _errorMessage.value = it.message ?: "An Error Occurred"
            }
        }
    }

    fun importFolder(pickedFolder: PickedFolder) {
        viewModelScope.launch {
            val result = importFolderUseCase(pickedFolder)

            result.onSuccess { summary ->
                loadDocuments()
                // Partial imports are reported through the same channel as
                // errors so the user still finds out which files were skipped.
                _errorMessage.value = if (summary.failed > 0) {
                    "Imported ${summary.imported}, skipped ${summary.failed}"
                } else {
                    null
                }
            }.onFailure {
                _errorMessage.value = it.message ?: "An Error Occurred"
            }
        }
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}