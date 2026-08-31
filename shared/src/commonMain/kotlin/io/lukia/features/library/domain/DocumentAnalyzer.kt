package io.lukia.features.library.domain

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.domain.document.PickedDocument

interface DocumentAnalyzer {
    suspend fun analyze(document: PickedDocument): DocumentInfo
}
