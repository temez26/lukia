package io.lukia.features.library.domain

import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.PickedDocument

interface DocumentAnalyzer {
    suspend fun analyze(document: PickedDocument): DocumentInfo
}
