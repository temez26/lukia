package io.lukia.core.infrastructure.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationCacheKey
import io.lukia.features.translate.infrastructure.SqlTranslationCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A library stored before translations were ever cached has to survive the
 * version that started caching them.
 *
 * The failure is quieter than a missing column and not much kinder: the table is
 * absent, every lookup throws, and translating stops working on exactly the
 * installations used longest. So the old schema is written out verbatim and
 * opened with the current driver, exactly as an installation would be.
 */
class TranslationCacheMigrationTest {

    /** The schema as it stood at version 2, before anything was cached. */
    private val version2Schema = """
        CREATE TABLE document (
            location TEXT NOT NULL PRIMARY KEY,
            fileName TEXT NOT NULL,
            extension TEXT NOT NULL,
            type TEXT NOT NULL,
            fileExists INTEGER NOT NULL,
            sizeBytes INTEGER NOT NULL,
            isFavorite INTEGER NOT NULL DEFAULT 0,
            coverImage BLOB,
            sortKey INTEGER NOT NULL,
            metaTitle TEXT,
            metaAuthors TEXT,
            metaPublisher TEXT,
            metaPublished TEXT,
            metaLanguage TEXT,
            metaDescription TEXT,
            metaSubjects TEXT,
            metaIdentifier TEXT
        );
        CREATE TABLE reading_position (
            location TEXT NOT NULL PRIMARY KEY,
            pageIndex INTEGER NOT NULL,
            pageCount INTEGER NOT NULL
        );
    """.trimIndent()

    private fun version2Database(): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        version2Schema.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { driver.execute(null, it, 0) }

        driver.execute(
            null,
            """
            INSERT INTO document(location, fileName, extension, type, fileExists, sizeBytes, isFavorite, coverImage, sortKey)
            VALUES ('/books/moby.epub', 'moby.epub', 'epub', 'EPUB', 1, 2048, 1, NULL, 1)
            """.trimIndent(),
            0,
        )

        driver.execute(null, "PRAGMA user_version = 2", 0)

        return driver
    }

    @Test
    fun aLibraryFromBeforeTheCacheCanCacheAfterwards() = runTest {
        val driver = version2Database()
        LukiaDatabase.Schema.migrate(driver, oldVersion = 2, newVersion = LukiaDatabase.Schema.version)

        val cache = SqlTranslationCache(LukiaDatabase(driver), Dispatchers.Unconfined)
        cache.put(TranslationCacheKey("a"), Translation("Kutsukaa minua Ismaeliksi."))

        assertEquals(
            "Kutsukaa minua Ismaeliksi.",
            assertNotNull(cache.get(TranslationCacheKey("a"))).text,
        )
    }

    /** And the books that were already on the shelf are still on it. */
    @Test
    fun theLibraryItselfIsUntouched() = runTest {
        val driver = version2Database()
        LukiaDatabase.Schema.migrate(driver, oldVersion = 2, newVersion = LukiaDatabase.Schema.version)

        val documents = LukiaDatabase(driver).documentQueries.selectAll().executeAsList()

        assertEquals(1, documents.size)
        assertEquals("moby.epub", documents.single().fileName)
        assertEquals(true, documents.single().isFavorite)
    }

    @Test
    fun theSchemaIsPastVersionTwoSoTheMigrationIsReachable() {
        assertTrue(
            LukiaDatabase.Schema.version > 2,
            "The cache table needs a schema version bump, or no installed " +
                "database will ever be migrated to it.",
        )
    }
}
