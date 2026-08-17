package io.lukia.core.infrastructure.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.lukia.features.library.infrastructure.SqlLibraryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A library stored by the version before metadata existed has to survive the
 * version that added it.
 *
 * This is the one change in the schema's life so far that an installed copy of
 * the app has to be carried across, and the failure mode is the worst kind: the
 * columns are missing, every query naming them throws, and the reader's whole
 * library appears to have vanished. So the old schema is written out here
 * verbatim and opened with the current driver, exactly as an existing
 * installation would be.
 */
class DocumentMigrationTest {

    /** `document` as it stood at schema version 1, before the metadata columns. */
    private val version1Schema = """
        CREATE TABLE document (
            location TEXT NOT NULL PRIMARY KEY,
            fileName TEXT NOT NULL,
            extension TEXT NOT NULL,
            type TEXT NOT NULL,
            fileExists INTEGER NOT NULL,
            sizeBytes INTEGER NOT NULL,
            isFavorite INTEGER NOT NULL DEFAULT 0,
            coverImage BLOB,
            sortKey INTEGER NOT NULL
        );
        CREATE TABLE reading_position (
            location TEXT NOT NULL PRIMARY KEY,
            pageIndex INTEGER NOT NULL,
            pageCount INTEGER NOT NULL
        );
    """.trimIndent()

    /**
     * A database holding one favourited book, stamped as version 1 - which is
     * what tells the driver there is a migration to run.
     */
    private fun version1Database(): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        version1Schema.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { driver.execute(null, it, 0) }

        driver.execute(
            null,
            """
            INSERT INTO document(location, fileName, extension, type, fileExists, sizeBytes, isFavorite, coverImage, sortKey)
            VALUES ('/books/moby.epub', 'moby.epub', 'epub', 'EPUB', 1, 2048, 1, NULL, 1)
            """.trimIndent(),
            0
        )

        driver.execute(null, "PRAGMA user_version = 1", 0)

        return driver
    }

    @Test
    fun aVersionOneLibrarySurvivesTheMetadataColumns() = runTest {
        val driver = version1Database()
        LukiaDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = LukiaDatabase.Schema.version)

        val repository = SqlLibraryRepository(LukiaDatabase(driver))
        val stored = repository.getDocument("/books/moby.epub")

        assertEquals("moby.epub", stored?.document?.fileName)
        assertEquals(2048L, stored?.sizeBytes)
        // Everything the reader had chosen is still theirs after the migration.
        assertEquals(true, stored?.isFavorite)
        // Nothing was read out of the file back then, so there is nothing to show.
        assertNull(stored?.metadata)
    }

    /**
     * The migration adds columns rather than backfilling them, so a book already
     * on the shelf fills in its metadata the next time it is imported - not by
     * the app reading every file it holds at startup.
     */
    @Test
    fun aMigratedBookTakesItsMetadataOnTheNextImport() = runTest {
        val driver = version1Database()
        LukiaDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = LukiaDatabase.Schema.version)

        val database = LukiaDatabase(driver)
        val repository = SqlLibraryRepository(database)

        val existing = repository.getDocument("/books/moby.epub")!!
        repository.addDocument(
            existing.copy(
                metadata = io.lukia.core.domain.BookMetadata(
                    title = "Moby Dick",
                    authors = listOf("Herman Melville")
                )
            )
        )

        val refreshed = repository.getDocument("/books/moby.epub")

        assertEquals("Moby Dick", refreshed?.metadata?.title)
        assertEquals(listOf("Herman Melville"), refreshed?.metadata?.authors)
        // ...and the shelf position and favourite the reader owned are untouched.
        assertEquals(true, refreshed?.isFavorite)
    }

    @Test
    fun theSchemaIsPastVersionOneSoTheMigrationIsReachable() {
        assertTrue(
            LukiaDatabase.Schema.version > 1,
            "The metadata columns need a schema version bump, or no installed " +
                "database will ever be migrated to them."
        )
    }
}
