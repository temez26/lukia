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
    /**
     * In the sync server's shared library. Read with [exists]: a server book
     * that does not exist here yet is one to download rather than one that
     * went missing - see docs/specs/sync-server.md.
     */
    val onServer: Boolean = false,
    /**
     * When the book came onto the shelf, in milliseconds since the epoch. Null
     * for one added before the app recorded it.
     */
    val addedAt: Long? = null,
) {
    /** On the sync server and not on this device yet: a book to download rather than one that went missing. */
    val awaitsDownload: Boolean get() = onServer && !exists

    val storage: BookStorage
        get() = when {
            exists && onServer -> BookStorage.Both
            exists -> BookStorage.Device
            onServer -> BookStorage.Server
            else -> BookStorage.Missing
        }
}

/** Where a book's file is kept: here, on the sync server, both, or nowhere it can be read from. */
enum class BookStorage {
    Device,
    Server,
    Both,
    Missing,
}
