package io.lukia.features.reader.infrastructure

import io.lukia.core.domain.PdfLayout
import io.lukia.core.domain.PdfLayoutRepository
import io.lukia.core.infrastructure.db.LukiaDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SqlPdfLayoutRepository(
    database: LukiaDatabase,
    /** Injectable so tests can run against a database deterministically. */
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
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
        layouts.put(location = location, layout = layout.name)
        Unit
    }

    override suspend fun forget(location: String) = withContext(dispatcher) {
        layouts.deleteByLocation(location)
        Unit
    }
}
