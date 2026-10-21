package io.lukia.core.infrastructure.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.lukia.core.domain.document.BookMetadata
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
 * The failure mode is the worst kind: the columns are missing, every query
 * naming them throws, and the reader's whole library appears to have vanished.
 * So the old schema is written out verbatim - see [versionOneDatabase] - and
 * opened with the current driver, exactly as an installation would be.
 */
class DocumentMigrationTest {

    /**
     * A database of that vintage holding one favourited book. The schema itself
     * is in [versionOneDatabase], because more than one test starts from it and
     * two copies of the oldest schema in the world would be two things to keep
     * in step.
     */
    private fun version1Database(): JdbcSqliteDriver {
        val driver = versionOneDatabase()

        driver.execute(
            null,
            """
            INSERT INTO document(location, fileName, extension, type, fileExists, sizeBytes, isFavorite, coverImage, sortKey)
            VALUES ('/books/moby.epub', 'moby.epub', 'epub', 'EPUB', 1, 2048, 1, NULL, 1)
            """.trimIndent(),
            0,
        )

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
                metadata = io.lukia.core.domain.document.BookMetadata(
                    title = "Moby Dick",
                    authors = listOf("Herman Melville"),
                ),
            ),
        )

        val refreshed = repository.getDocument("/books/moby.epub")

        assertEquals("Moby Dick", refreshed?.metadata?.title)
        assertEquals(listOf("Herman Melville"), refreshed?.metadata?.authors)
        // ...and the shelf position and favourite the reader owned are untouched.
        assertEquals(true, refreshed?.isFavorite)
    }

    /**
     * When a book already on the shelf was added was never written down, so it
     * stays unknown; a book added after the migration is stamped as it arrives.
     */
    @Test
    fun onlyBooksAddedAfterTheMigrationKnowWhenTheyCame() = runTest {
        val driver = version1Database()
        LukiaDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = LukiaDatabase.Schema.version)
        val repository = SqlLibraryRepository(LukiaDatabase(driver))

        val before = System.currentTimeMillis()
        val existing = repository.getDocument("/books/moby.epub")!!
        repository.addDocument(existing.copy(document = existing.document.copy(location = "/books/new.epub")))

        assertNull(repository.getDocument("/books/moby.epub")?.addedAt)
        val added = repository.getDocuments().single { it.document.location == "/books/new.epub" }.addedAt
        assertTrue(added != null && added >= before - 1_000L, "added at $added, test began at $before")
    }

    @Test
    fun theSchemaIsPastVersionOneSoTheMigrationIsReachable() {
        assertTrue(
            LukiaDatabase.Schema.version > 1,
            "The metadata columns need a schema version bump, or no installed " +
                "database will ever be migrated to them.",
        )
    }
}
