package io.lukia.features.import.domain

import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.PickedDocument

interface DocumentAnalyzer {
    suspend fun analyze(document: PickedDocument): DocumentInfo
}
