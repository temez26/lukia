package io.lukia.features.import.infrastructure

import io.lukia.features.import.domain.entities.ImportedDocument
import io.lukia.features.import.domain.repositories.DocumentImportRepository

class DocumentImportRepositoryImpl(
    private val picker: DocumentPicker,
    private val storage: DocumentStorage,
    private val analyzer: DocumentAnalyzer
) : DocumentImportRepository {

    override suspend fun importDocument(): ImportedDocument {

        val picked = picker.pick()
            ?: throw IllegalStateException("User cancelled import")

        val stored = storage.store(picked)

        val metadata = analyzer.analyze(stored)

        return ImportedDocument(
            id = stored.id,
            title = metadata.title,
            path = stored.path,
            type = metadata.type
        )
    }
}