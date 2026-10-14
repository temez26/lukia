package io.lukia.features.library.infrastructure

import io.lukia.core.domain.document.PickedDocument
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.ioDispatcher
import io.lukia.features.library.domain.ContentHashStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class SqlContentHashStore(
    private val database: LukiaDatabase,
    /** Injectable so tests can run against a database deterministically. */
    private val dispatcher: CoroutineDispatcher = ioDispatcher(),
) : ContentHashStore {

    override suspend fun unhashed(): List<PickedDocument> = withContext(dispatcher) {
        database.documentQueries.selectUnhashed().executeAsList().map {
            PickedDocument(fileName = it.fileName, extension = it.extension, location = it.location)
        }
    }

    /** The book and its sittings together, so a sitting is never left nameless beside a named book. */
    override suspend fun record(location: String, contentHash: String) = withContext(dispatcher) {
        database.transaction {
            database.documentQueries.setContentHash(contentHash, location)
            database.readingSessionQueries.nameBook(contentHash = contentHash, location = location)
        }
    }
}
