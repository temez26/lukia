package io.lukia.features.import.infrastructure

import io.lukia.features.import.domain.entities.DocumentType

data class DocumentMetadata(
    val title: String,
    val type: DocumentType
)