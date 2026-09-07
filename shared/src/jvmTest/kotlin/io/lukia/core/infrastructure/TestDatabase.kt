package io.lukia.core.infrastructure

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.lukia.core.infrastructure.db.LukiaDatabase
import java.util.Properties

/**
 * A database of its own, in memory, for one test.
 *
 * The real driver against real SQLite rather than a fake repository: what these
 * tests are actually about is the SQL - which statement preserves a favourite,
 * what a replace does to a shelf position - and a stand-in would answer those
 * questions with whatever the stand-in was written to believe.
 *
 * Opened with foreign keys on, which is not SQLite's default and is what the
 * app does - see `LukiaDriver.jvm.kt`. Without this the tests would run against
 * a database with different rules from the one every reader has: a position
 * saved for a book no library holds would be accepted here and rejected there,
 * and the cascade that removing a book relies on would not fire.
 */
internal fun testDatabase(): LukiaDatabase = LukiaDatabase(
    JdbcSqliteDriver(
        url = JdbcSqliteDriver.IN_MEMORY,
        properties = Properties().apply { setProperty("foreign_keys", "true") },
        schema = LukiaDatabase.Schema,
    ),
)

/**
 * Puts a book on the shelf, so that something keyed to it has something to be
 * keyed to.
 *
 * Everything the reader stores about a book - where they were in it, how they
 * chose to read it - now references the library row, so a test about those has
 * to import the book first, exactly as the app does. The columns are the fewest
 * that satisfy the table; nothing here is about what is in them.
 */
internal fun LukiaDatabase.shelve(vararg locations: String) = locations.forEach { location ->
    documentQueries.insertIfAbsent(
        location = location,
        fileName = location.substringAfterLast('/'),
        extension = location.substringAfterLast('.', missingDelimiterValue = ""),
        type = "PDF",
        fileExists = true,
        sizeBytes = 1024L,
        coverImage = null,
        metaTitle = null,
        metaAuthors = null,
        metaPublisher = null,
        metaPublished = null,
        metaLanguage = null,
        metaDescription = null,
        metaSubjects = null,
        metaIdentifier = null,
    )
}
