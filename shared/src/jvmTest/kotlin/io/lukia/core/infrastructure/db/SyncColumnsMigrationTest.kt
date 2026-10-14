package io.lukia.core.infrastructure.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Migration 11, to version 12, adds what syncing with a server needs: a
 * content hash per book, and an id, hash and title per reading session.
 * Everybody updating has sessions already, and those must come through it with
 * their hours intact and an id each - a session without one could never be
 * sent, and two sharing one would be counted as one.
 */
class SyncColumnsMigrationTest {

    @Test
    fun everyExistingSessionGetsAnIdOfItsOwn() {
        val driver = versionElevenDatabase()
        repeat(50) { driver.execute(null, insertSession("file:///moby.pdf", it * 1000L), 0) }

        LukiaDatabase.Schema.migrate(driver, oldVersion = 11, newVersion = 12)

        val ids = driver.strings("SELECT uuid FROM reading_session")
        assertEquals(50, ids.size)
        assertEquals(50, ids.toSet().size, "every session's id is unique")
        ids.forEach { id ->
            assertEquals(id, UUID.fromString(id).toString(), "$id is a UUID as it would be printed")
            assertEquals(4, UUID.fromString(id).version(), "$id is a random (version 4) UUID")
        }
    }

    @Test
    fun aSessionKeepsItsHoursAndLearnsItsBooksTitle() {
        val driver = versionElevenDatabase()
        driver.execute(
            null,
            """
            INSERT INTO document(location, fileName, extension, type, fileExists, sizeBytes, sortKey, metaTitle)
            VALUES ('file:///moby.pdf', 'moby.pdf', 'pdf', 'PDF', 1, 10, 1, 'Moby Dick')
            """.trimIndent(),
            0,
        )
        driver.execute(null, insertSession("file:///moby.pdf", 5_000), 0)
        driver.execute(null, insertSession("file:///gone.pdf", 6_000), 0)

        LukiaDatabase.Schema.migrate(driver, oldVersion = 11, newVersion = 12)

        assertEquals(
            listOf("file:///moby.pdf 5000 Moby Dick", "file:///gone.pdf 6000 -"),
            driver.strings(
                "SELECT location || ' ' || durationMs || ' ' || COALESCE(title, '-') FROM reading_session ORDER BY id",
            ),
        )
    }

    @Test
    fun noBookIsHashedByTheMigration() {
        val driver = versionElevenDatabase()
        driver.execute(
            null,
            """
            INSERT INTO document(location, fileName, extension, type, fileExists, sizeBytes, sortKey)
            VALUES ('file:///moby.pdf', 'moby.pdf', 'pdf', 'PDF', 1, 10, 1)
            """.trimIndent(),
            0,
        )

        LukiaDatabase.Schema.migrate(driver, oldVersion = 11, newVersion = 12)

        assertTrue(driver.strings("SELECT contentHash FROM document WHERE contentHash IS NOT NULL").isEmpty())
        assertTrue(driver.strings("SELECT kind FROM sync_outbox").isEmpty())
    }

    private fun versionElevenDatabase(): JdbcSqliteDriver = versionOneDatabase().also {
        LukiaDatabase.Schema.migrate(it, oldVersion = 1, newVersion = 11)
    }

    private fun insertSession(location: String, durationMs: Long) =
        "INSERT INTO reading_session(location, startedAt, durationMs) VALUES ('$location', 1, $durationMs)"

    private fun JdbcSqliteDriver.strings(sql: String): List<String> = executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            val rows = mutableListOf<String>()
            while (cursor.next().value) rows += cursor.getString(0).orEmpty()
            QueryResult.Value(rows)
        },
        parameters = 0,
    ).value
}
