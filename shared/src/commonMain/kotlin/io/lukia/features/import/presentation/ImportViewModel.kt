package io.lukia.features.import.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.features.import.domain.DocumentInfo
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.ImportDocumentUseCase
import io.lukia.features.import.infrastructure.ImportFilePicker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ImportViewModel(
    private val importFilePicker: ImportFilePicker,
    private val importDocumentUseCase: ImportDocumentUseCase
) : ViewModel() {
    private val _documentInfo = MutableStateFlow<DocumentInfo?>(null)
    val documentInfo: StateFlow<DocumentInfo?> = _documentInfo.asStateFlow()

    fun importDocument() {
        viewModelScope.launch {
            val result = importFilePicker.pick()
            if (result != null) {
                _documentInfo.value = importDocumentUseCase.execute(result)
            }
        }
    }
}
