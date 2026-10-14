package io.lukia.server.library

import io.ktor.http.HttpStatusCode
import io.lukia.api.ApiErrorCodes
import io.lukia.api.BookResponse
import io.lukia.api.ChangePage
import io.lukia.core.domain.document.BookMetadata
import io.lukia.core.domain.document.DocumentType
import io.lukia.core.infrastructure.importing.DocumentContents
import io.lukia.core.infrastructure.importing.JvmDocumentSource
import io.lukia.core.infrastructure.importing.extractContents
import io.lukia.server.db.Book
import io.lukia.server.db.ServerDatabase
import io.lukia.server.http.ApiException
import io.lukia.server.http.changePage
import java.io.File
import java.time.Clock

/**
 * The shared library: adding, listing, fetching and removing books.
 *
 * Blocking throughout - JDBC and the format readers both are - so routes call
 * it through `blocking`.
 */
class LibraryService(
    private val database: ServerDatabase,
    private val files: BookFiles,
    private val clock: Clock,
) {
    private val books get() = database.bookQueries

    /** The outcome of an upload, and whether it added anything. */
    class Added(val book: BookResponse, val isNew: Boolean)

    /**
     * Files [upload] as a book of the type its [name] gives, read for its metadata and cover the
     * way the app's import reads one. A book already on the shelf is answered
     * with the copy there, and the upload thrown away.
     */
    fun add(upload: ReceivedUpload, name: UploadName, addedBy: Long): Added {
        val fileName = name.fileName
        val type = name.type
        val existing = books.selectById(upload.id).executeAsOneOrNull()
        if (existing != null && !existing.deleted) {
            upload.file.delete()
            return Added(existing.toResponse(), isNew = false)
        }

        val contents = readContents(upload.file, type)
        val metadata = contents.metadata ?: BookMetadata.Empty
        val cover = contents.coverImage?.bytes

        // The file goes into place before the row exists, so no row ever names a missing file.
        files.store(upload.file, upload.id)

        database.transaction {
            val now = clock.millis()
            if (existing == null) {
                books.insert(
                    upload.id, fileName, type.name, upload.sizeBytes, cover != null,
                    metadata.title, metadata.authors.joinLines(), metadata.publisher, metadata.published,
                    metadata.language, metadata.description, metadata.subjects.joinLines(), metadata.identifier,
                    addedBy, now,
                )
            } else {
                books.restore(
                    fileName = fileName,
                    type = type.name,
                    sizeBytes = upload.sizeBytes,
                    hasCover = cover != null,
                    metaTitle = metadata.title,
                    metaAuthors = metadata.authors.joinLines(),
                    metaPublisher = metadata.publisher,
                    metaPublished = metadata.published,
                    metaLanguage = metadata.language,
                    metaDescription = metadata.description,
                    metaSubjects = metadata.subjects.joinLines(),
                    metaIdentifier = metadata.identifier,
                    addedBy = addedBy,
                    addedAt = now,
                    id = upload.id,
                )
            }
            cover?.let { books.insertCover(upload.id, it) }
        }

        return Added(books.selectById(upload.id).executeAsOne().toResponse(), isNew = true)
    }

    fun changesSince(since: Long, limit: Int): ChangePage<BookResponse> =
        changePage(
            since,
            limit,
            fetch = { after, count -> books.selectSince(after, count).executeAsList() },
            seqOf = Book::seq,
            toItem = { it.toResponse() },
        )

    fun fileOf(id: String): File {
        val book = books.selectById(id).executeAsOneOrNull()?.takeUnless { it.deleted }
        return book?.let { files.fileOf(id) }?.takeIf(File::isFile) ?: throw notFound()
    }

    fun coverOf(id: String): ByteArray = books.selectCover(id).executeAsOneOrNull() ?: throw notFound()

    fun delete(id: String) {
        database.transaction {
            books.markDeleted(id)
            if (database.accountQueries.changes().executeAsOne() == 0L) throw notFound()
            books.deleteCover(id)
        }
        files.remove(id)
    }

    /** What the app's desktop import does, on the calling thread: a file that will not open is a book with nothing to say. */
    private fun readContents(file: File, type: DocumentType): DocumentContents =
        runCatching { JvmDocumentSource(file) }.getOrNull()
            ?.use { extractContents(type, it) }
            ?: DocumentContents.Empty

    private fun notFound() = ApiException(HttpStatusCode.NotFound, ApiErrorCodes.NotFound, "No such book.")
}

private fun List<String>.joinLines(): String? = takeIf { it.isNotEmpty() }?.joinToString("\n")

private fun String?.splitLines(): List<String> = this?.split('\n').orEmpty()

private fun Book.toResponse() = BookResponse(
    id = id,
    fileName = fileName,
    type = type,
    sizeBytes = sizeBytes,
    hasCover = hasCover,
    title = metaTitle,
    authors = metaAuthors.splitLines(),
    publisher = metaPublisher,
    published = metaPublished,
    language = metaLanguage,
    description = metaDescription,
    subjects = metaSubjects.splitLines(),
    identifier = metaIdentifier,
    addedAt = addedAt,
    deleted = deleted,
)
