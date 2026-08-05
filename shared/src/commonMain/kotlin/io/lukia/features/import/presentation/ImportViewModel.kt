package io.lukia.features.import.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.lukia.features.import.domain.DocumentInfo
import io.lukia.features.import.domain.DocumentAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ImportViewModel(
    private val importFilePicker: ImportFilePicker,
    private val documentAnalyzer: DocumentAnalyzer
) : ViewModel() {
    private val _documentInfo = MutableStateFlow<DocumentInfo?>(null)
    val documentInfo: StateFlow<DocumentInfo?> = _documentInfo.asStateFlow()

    fun importDocument() {
        viewModelScope.launch {
            val result = importFilePicker.pick()
            if (result != null) {
                _documentInfo.value = documentAnalyzer.analyze(result)
            }
        }
    }
}
