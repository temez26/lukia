package io.lukia.features.import.domain.repositories

import io.lukia.features.import.domain.entities.ImportedDocument

interface DocumentImportRepository {
    suspend fun importDocument(): ImportedDocument
}