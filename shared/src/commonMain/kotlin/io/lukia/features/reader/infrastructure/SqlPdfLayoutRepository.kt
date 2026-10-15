package io.lukia.features.reader.infrastructure

import io.lukia.core.domain.reading.PdfLayout
import io.lukia.core.domain.reading.PdfLayoutRepository
import io.lukia.core.infrastructure.SyncedPart
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.ioDispatcher
import io.lukia.core.infrastructure.noteChange
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class SqlPdfLayoutRepository(
    private val database: LukiaDatabase,
    /** Injectable so tests can run against a database deterministically. */
    private val dispatcher: CoroutineDispatcher = ioDispatcher(),
) : PdfLayoutRepository {

    private val layouts = database.readerLayoutQueries

    /**
     * Stored by name, so a value written by a build that knew a layout this one
     * does not reads as no choice at all rather than throwing - the same rule
     * the key-value settings follow, and for the same reason.
     */
    override suspend fun layoutFor(location: String): PdfLayout? = withContext(dispatcher) {
        val stored = layouts.selectByLocation(location).executeAsOneOrNull()

        PdfLayout.entries.firstOrNull { it.name == stored }
    }

    override suspend fun save(location: String, layout: PdfLayout) = withContext(dispatcher) {
        database.transaction {
            layouts.put(location = location, layout = layout.name)
            database.noteChange(SyncedPart.Layout, location)
        }
    }

    override suspend fun forget(location: String) = withContext(dispatcher) {
        layouts.deleteByLocation(location)
        Unit
    }
}
