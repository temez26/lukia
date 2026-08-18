package io.lukia.features.library.infrastructure

import io.lukia.core.domain.BookMetadata
import io.lukia.core.domain.CoverImage
import io.lukia.core.domain.DocumentInfo
import io.lukia.core.domain.DocumentType
import io.lukia.core.domain.PickedDocument
import io.lukia.core.infrastructure.db.Document
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.features.library.domain.LibraryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The library, kept in the app's database so that it is still there next time.
 *
 * Reads and writes are moved off the caller's thread. Every caller is a
 * ViewModel scope, which is main-dispatched, and one of these reads carries the
 * cover art of every book in the library - hundreds of kilobytes at a time,
 * which is not something to do on the thread that draws.
 */
class SqlLibraryRepository(
    database: LukiaDatabase,
    /** Injectable so tests can run against a database deterministically. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : LibraryRepository {

    private val documents = database.documentQueries

    /**
     * Two statements in one transaction: add the book if the library has never
     * seen it, then bring what the analyser knows up to date. Together they say
     * "store this", while leaving the shelf position and the favourite - which
     * belong to the reader, not to the file - exactly as they were.
     */
    override suspend fun addDocument(document: DocumentInfo) = withContext(dispatcher) {
        val picked = document.document
        val metadata = document.metadata

        documents.transaction {
            documents.insertIfAbsent(
                location = picked.location,
                fileName = picked.fileName,
                extension = picked.extension,
                type = document.type.name,
                fileExists = document.exists,
                sizeBytes = document.sizeBytes,
                coverImage = document.coverImage?.bytes,
                metaTitle = metadata?.title,
                metaAuthors = metadata?.authors.toStoredList(),
                metaPublisher = metadata?.publisher,
                metaPublished = metadata?.published,
                metaLanguage = metadata?.language,
                metaDescription = metadata?.description,
                metaSubjects = metadata?.subjects.toStoredList(),
                metaIdentifier = metadata?.identifier
            )

            documents.refresh(
                fileName = picked.fileName,
                extension = picked.extension,
                type = document.type.name,
                fileExists = document.exists,
                sizeBytes = document.sizeBytes,
                coverImage = document.coverImage?.bytes,
                hasMetadata = metadata != null,
                metaTitle = metadata?.title,
                metaAuthors = metadata?.authors.toStoredList(),
                metaPublisher = metadata?.publisher,
                metaPublished = metadata?.published,
                metaLanguage = metadata?.language,
                metaDescription = metadata?.description,
                metaSubjects = metadata?.subjects.toStoredList(),
                metaIdentifier = metadata?.identifier,
                location = picked.location
            )
        }
    }

    override suspend fun getDocuments(): List<DocumentInfo> = withContext(dispatcher) {
        documents.selectAll().executeAsList().map { it.toDocumentInfo() }
    }

    override suspend fun getDocument(location: String): DocumentInfo? = withContext(dispatcher) {
        documents.selectByLocation(location).executeAsOneOrNull()?.toDocumentInfo()
    }

    override suspend fun knownLocations(): Set<String> = withContext(dispatcher) {
        documents.selectLocations().executeAsList().toSet()
    }

    override suspend fun setFavorite(location: String, isFavorite: Boolean) =
        withContext(dispatcher) {
            // A statement that matches no row is already the no-op the contract
            // asks for, so an unknown location needs no check of its own.
            documents.setFavorite(isFavorite = isFavorite, location = location)
            Unit
        }

    override suspend fun removeDocument(location: String) = withContext(dispatcher) {
        documents.deleteByLocation(location)
        Unit
    }
}

/**
 * A stored row as the rest of the app knows it.
 *
 * The type is stored by name rather than by ordinal: a name survives an enum
 * gaining a case in the middle, and an ordinal quietly turns every PDF in the
 * library into an EPUB. A name that no longer resolves reads as [UNKNOWN],
 * which is the case the app already handles for a file it cannot open.
 */
private fun Document.toDocumentInfo(): DocumentInfo = DocumentInfo(
    document = PickedDocument(
        fileName = fileName,
        extension = extension,
        location = location
    ),
    type = DocumentType.entries.firstOrNull { it.name == type } ?: DocumentType.UNKNOWN,
    exists = fileExists,
    sizeBytes = sizeBytes,
    coverImage = coverImage?.let { CoverImage(it) },
    isFavorite = isFavorite,
    metadata = storedMetadata()
)

/**
 * The metadata columns as one value, or null when the row carries none - which
 * is every row imported before this was stored, and every plain text file.
 */
private fun Document.storedMetadata(): BookMetadata? {
    val metadata = BookMetadata(
        title = metaTitle,
        authors = metaAuthors.fromStoredList(),
        publisher = metaPublisher,
        published = metaPublished,
        language = metaLanguage,
        description = metaDescription,
        subjects = metaSubjects.fromStoredList(),
        identifier = metaIdentifier
    )

    return metadata.takeIf { !it.isEmpty }
}

/**
 * A list of short strings in one column, newline-separated. See the note on the
 * table in `Document.sq` for why they are not in tables of their own.
 *
 * An empty list stores as null rather than as an empty string, so "no authors"
 * reads back the same whether the column was never written or written empty.
 */
private fun List<String>?.toStoredList(): String? =
    this?.takeIf { it.isNotEmpty() }?.joinToString(separator = "\n")

private fun String?.fromStoredList(): List<String> =
    this?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
