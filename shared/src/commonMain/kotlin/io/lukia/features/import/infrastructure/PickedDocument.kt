package io.lukia.features.import.infrastructure

data class PickedDocument (
    val name: String,
    val extension: String,
    val source: DocumentSource,
)
