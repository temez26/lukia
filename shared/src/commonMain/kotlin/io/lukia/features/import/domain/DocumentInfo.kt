package io.lukia.features.import.domain

data class DocumentInfo(
    val document: PickedDocument,
    val type: DocumentType,
    val exists: Boolean,
    val sizeBytes: Long,
    val coverImage: CoverImage? = null
)
