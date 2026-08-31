package io.lukia.core.domain.document

data class DocumentInfo(
    val document: PickedDocument,
    val type: DocumentType,
    val exists: Boolean,
    val sizeBytes: Long,
    val coverImage: CoverImage? = null,
    val isFavorite: Boolean = false,
    /**
     * What the document says about itself, read at import time. Null for a
     * format that carries none and for one that carried none worth keeping -
     * see [BookMetadata].
     */
    val metadata: BookMetadata? = null,
)
