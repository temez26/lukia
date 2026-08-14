package io.lukia.core.domain

data class DocumentInfo(
    val document: PickedDocument,
    val type: DocumentType,
    val exists: Boolean,
    val sizeBytes: Long,
    val coverImage: CoverImage? = null,
    val isFavorite: Boolean = false
)
