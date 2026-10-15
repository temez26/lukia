package io.lukia.features.library.infrastructure

import io.lukia.core.domain.document.DocumentInfo
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.ioDispatcher
import io.lukia.features.library.domain.ServerShelf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class SqlServerShelf(
    private val database: LukiaDatabase,
    /** Injectable so tests can run against a database deterministically. */
    private val dispatcher: CoroutineDispatcher = ioDispatcher(),
) : ServerShelf {

    private val documents get() = database.documentQueries

    override suspend fun locationOf(contentHash: String): String? = withContext(dispatcher) {
        documents.selectLocationByHash(contentHash).executeAsOneOrNull()
    }

    override suspend fun addServerBook(document: DocumentInfo, contentHash: String) = withContext(dispatcher) {
        val metadata = document.metadata
        documents.insertServerBook(
            location = document.document.location,
            fileName = document.document.fileName,
            extension = document.document.extension,
            type = document.type.name,
            sizeBytes = document.sizeBytes,
            coverImage = document.coverImage?.bytes,
            metaTitle = metadata?.title,
            metaAuthors = metadata?.authors.toStoredList(),
            metaPublisher = metadata?.publisher,
            metaPublished = metadata?.published,
            metaLanguage = metadata?.language,
            metaDescription = metadata?.description,
            metaSubjects = metadata?.subjects.toStoredList(),
            metaIdentifier = metadata?.identifier,
            contentHash = contentHash,
        )
        Unit
    }

    override suspend fun markUploaded(location: String, contentHash: String) = withContext(dispatcher) {
        database.transaction {
            database.recordContentHash(location, contentHash)
            documents.setOnServer(true, contentHash)
        }
    }

    override suspend fun markOnServer(contentHash: String, onServer: Boolean) = withContext(dispatcher) {
        documents.setOnServer(onServer, contentHash)
        Unit
    }

    override suspend fun removeUndownloaded(contentHash: String) = withContext(dispatcher) {
        documents.deleteUndownloadedByHash(contentHash)
        Unit
    }

    override suspend fun forgetServer() = withContext(dispatcher) {
        database.transaction {
            documents.deleteAllUndownloaded()
            documents.clearOnServer()
        }
    }

    override suspend fun serverBookAt(location: String): Pair<String, String>? = withContext(dispatcher) {
        documents.selectServerBook(location).executeAsOneOrNull()?.let { it.contentHash to it.extension }
    }

    override suspend fun markDownloaded(location: String) = withContext(dispatcher) {
        documents.markDownloaded(location)
        Unit
    }

    override suspend fun newestPosition(): Long = withContext(dispatcher) {
        documents.selectNewestSortKey().executeAsOne()
    }

    override suspend fun localOnlyAfter(position: Long): List<String> = withContext(dispatcher) {
        documents.selectLocalOnlyAfter(position).executeAsList()
    }
}
