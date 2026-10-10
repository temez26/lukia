package io.lukia.core.infrastructure.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.lukia.core.domain.reading.ReadingProgress
import io.lukia.features.reader.infrastructure.SqlReadingProgressRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A library that was read before the app recorded which book was read last has
 * to survive the version that started recording it.
 *
 * The positions are the thing to protect: where somebody is in a book they are
 * part way through, and the one piece of state the app cannot re-derive from the
 * files. So the old schema is written out verbatim and opened with the current
 * driver, exactly as an installation would be.
 */
class ReadingPositionMigrationTest {

    /** The schema as it stood at version 3, before books were ordered by what was read last. */
    private val version3Schema = """
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
        CREATE TABLE translation_cache (
            cacheKey TEXT NOT NULL PRIMARY KEY,
            translation TEXT NOT NULL,
            note TEXT,
            usedAt INTEGER NOT NULL
        );
        CREATE INDEX translation_cache_usedAt ON translation_cache(usedAt);
    """.trimIndent()

    private fun version3Database(): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        version3Schema.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { driver.execute(null, it, 0) }

        // Both books are on the shelf, as they would be in a real library of
        // this vintage: nothing could open a book the library did not list, so
        // nothing could store a position for one. Version 10 leans on that when
        // it keys positions to documents, and drops any that are not.
        listOf("/books/moby.epub", "/books/dune.pdf").forEachIndexed { index, location ->
            val fileName = location.substringAfterLast('/')
            val sortKey = index + 1

            driver.execute(
                null,
                """
                INSERT INTO document(location, fileName, extension, type, fileExists, sizeBytes, isFavorite, coverImage, sortKey)
                VALUES ('$location', '$fileName', 'epub', 'EPUB', 1, 2048, 0, NULL, $sortKey)
                """.trimIndent(),
                0,
            )
        }

        driver.execute(
            null,
            """
            INSERT INTO reading_position(location, pageIndex, pageCount)
            VALUES ('/books/moby.epub', 41, 300)
            """.trimIndent(),
            0,
        )

        driver.execute(null, "PRAGMA user_version = 3", 0)

        return driver
    }

    private fun migrated(): JdbcSqliteDriver = version3Database().also {
        LukiaDatabase.Schema.migrate(it, oldVersion = 3, newVersion = LukiaDatabase.Schema.version)
    }

    @Test
    fun aPositionStoredBeforeTheColumnExistedIsStillWhereItWas() = runTest {
        val repository = SqlReadingProgressRepository(
            LukiaDatabase(migrated()),
            Dispatchers.Unconfined,
        )

        val stored = assertNotNull(repository.progressFor("/books/moby.epub"))

        assertEquals(41, stored.pageIndex)
        assertEquals(300, stored.pageCount)
    }

    /**
     * Nothing can be invented about when it was read, so it takes the bottom of
     * the order and corrects itself the next time the book is opened.
     */
    @Test
    fun aPositionFromBeforeCountingBeganRanksBelowOneWrittenSince() = runTest {
        val repository = SqlReadingProgressRepository(
            LukiaDatabase(migrated()),
            Dispatchers.Unconfined,
        )

        repository.save("/books/dune.pdf", ReadingProgress(pageIndex = 1, pageCount = 100))

        val positions = repository.allProgress()

        assertEquals(0L, positions.getValue("/books/moby.epub").readAt)
        assertTrue(positions.getValue("/books/dune.pdf").readAt > 0L)
    }

    /**
     * Version 10 keys a position to the book it is about, and can only do that
     * by dropping the ones pointing at no book. Nothing could have created one -
     * the reader refuses to open a location the library does not list - but a
     * database is not a proof, so this is what says the migration takes only
     * those and leaves every position with a book still on the shelf.
     */
    @Test
    fun aPositionForABookNoLongerInTheLibraryDoesNotSurviveTheKey() = runTest {
        val driver = version3Database()

        driver.execute(
            null,
            """
            INSERT INTO reading_position(location, pageIndex, pageCount)
            VALUES ('/books/vanished.epub', 7, 90)
            """.trimIndent(),
            0,
        )

        LukiaDatabase.Schema.migrate(driver, oldVersion = 3, newVersion = LukiaDatabase.Schema.version)

        val repository = SqlReadingProgressRepository(LukiaDatabase(driver), Dispatchers.Unconfined)

        assertNull(repository.progressFor("/books/vanished.epub"))
        assertNotNull(repository.progressFor("/books/moby.epub"))
    }

    @Test
    fun theSchemaIsPastVersionThreeSoTheMigrationIsReachable() {
        assertTrue(LukiaDatabase.Schema.version >= 4)
    }
}
