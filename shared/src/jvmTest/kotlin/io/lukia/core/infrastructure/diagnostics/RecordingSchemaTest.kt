package io.lukia.core.infrastructure.diagnostics

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.DiagnosticLevel
import io.lukia.core.infrastructure.db.LukiaDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Driven through the real schema and a real SQLite file, because the whole point
 * of this wrapper is what a driver does with it - a stand-in schema would prove
 * only that a delegate delegates.
 */
class RecordingSchemaTest {

    @Test
    fun creatingTheSchemaIsRecordedOnce() {
        val app = Recording()

        app.schema.create(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))

        val entry = app.buffer.entries.value.single()

        assertEquals("schema-created", entry.event)
        assertEquals(DiagnosticArea.Storage, entry.area)
        assertEquals("version ${LukiaDatabase.Schema.version}", entry.detail)
    }

    /**
     * The version pair is the whole of what makes a migration entry useful: it
     * says which step of the app's history this reader's library came through.
     */
    @Test
    fun aMigrationIsRecordedWithTheVersionsItCrossed() {
        val app = Recording()
        val driver = versionOneDatabase()

        app.schema.migrate(driver, oldVersion = 1, newVersion = LukiaDatabase.Schema.version)

        val entry = app.buffer.entries.value.single()

        assertEquals("schema-migrated", entry.event)
        assertEquals("1 -> ${LukiaDatabase.Schema.version}", entry.detail)
        assertEquals(DiagnosticLevel.Info, entry.level)
    }

    /**
     * The failure a reader cannot describe: the app dies on its next query with
     * nothing saying which step it died on. It has to be recorded, and it has to
     * still be thrown - swallowing it would leave the app running on a database
     * whose shape it does not know.
     */
    @Test
    fun aMigrationThatFailsIsRecordedAndStillThrown() {
        val app = Recording()

        // A database stamped as version 1 while holding none of version 1's
        // tables, so the first statement of the migration has nothing to alter.
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(null, "PRAGMA user_version = 1", 0)

        assertFailsWith<Throwable> {
            app.schema.migrate(driver, oldVersion = 1, newVersion = LukiaDatabase.Schema.version)
        }

        val entry = app.buffer.entries.value.single()

        assertEquals("migration-failed", entry.event)
        assertEquals(DiagnosticLevel.Failure, entry.level)
        assertEquals("1 -> ${LukiaDatabase.Schema.version}", entry.detail)
        assertTrue(entry.causeType != null, "the failure was recorded without its type")
    }

    @Test
    fun nothingIsRecordedWhileDiagnosticsAreOff() {
        val app = Recording(enabled = false)

        app.schema.create(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY))

        assertEquals(emptyList(), app.buffer.entries.value)
    }

    @Test
    fun theWrappedSchemaKeepsItsOwnVersion() {
        assertEquals(LukiaDatabase.Schema.version, Recording().schema.version)
    }

    private class Recording(enabled: Boolean = true) {
        val buffer = DiagnosticBuffer()
        val schema = LukiaDatabase.Schema.recordingTo(
            RecordingDiagnostics(buffer = buffer, enabled = { enabled }),
        )
    }

    /** Version 1's tables, stamped as version 1, which is what asks for a migration. */
    private fun versionOneDatabase(): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

        val version1Schema = """
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

        version1Schema.split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { driver.execute(null, it, 0) }

        driver.execute(null, "PRAGMA user_version = 1", 0)

        return driver
    }
}
