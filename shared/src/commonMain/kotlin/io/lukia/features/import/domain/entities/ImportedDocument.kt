package io.lukia.features.import.domain.entities

data class ImportedDocument (
    val id: String,
    val title: String,
    val path: String,
    val type: DocumentType
)