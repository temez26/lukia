package io.lukia.core.infrastructure.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A reader who skipped every update in between.
 *
 * The migration tests beside this each take one step and say what it did to the
 * data. This asks whether the steps still add up. An install arriving at the
 * current version from six back is the ordinary case, and the way it goes wrong
 * is not a query failing loudly but a table quietly ending up a column, a
 * default or a foreign key different from what every generated statement was
 * written against.
 *
 * It also catches the cheapest mistake here: a column added to a `.sq` file with
 * no `.sqm` beside it. That compiles, generates, and passes every test written
 * against a database created from scratch - then throws on the first query of
 * the first launch after an update, for everyone who had the app already and
 * nobody who installs it new. Here the walk up from an old version never gains
 * the column, and the comparison says so.
 */
class SchemaVersionJumpTest {

    /**
     * Every version an installed database can be sitting at, taken to the
     * current one in a single jump, has to arrive at the schema the app's
     * statements are generated against.
     */
    @Test
    fun everyVersionStillInTheWorldArrivesAtTheCurrentSchema() {
        val current = structureOf(currentDatabase())

        for (version in 1 until LukiaDatabase.Schema.version) {
            val installed = databaseAtVersion(version)

            LukiaDatabase.Schema.migrate(
                installed,
                oldVersion = version,
                newVersion = LukiaDatabase.Schema.version,
            )

            assertEquals(
                current,
                structureOf(installed),
                "a database at version $version does not reach the current schema in one jump",
            )
        }
    }

    /**
     * And the one-update-at-a-time walk has to end in the same place, so that a
     * reader who took every update is not left on a different schema from one
     * who took none of them until today.
     */
    @Test
    fun theStepByStepWalkEndsWhereTheJumpDoes() {
        val stepped = versionOneDatabase()

        for (version in 1 until LukiaDatabase.Schema.version) {
            LukiaDatabase.Schema.migrate(stepped, oldVersion = version, newVersion = version + 1)
        }

        assertEquals(structureOf(currentDatabase()), structureOf(stepped))
    }

    /**
     * The version a new database is stamped with has to be past every migration
     * that ships, or the ones above it never run against anything.
     */
    @Test
    fun theSchemaIsPastEveryMigrationItShips() {
        assertTrue(
            LukiaDatabase.Schema.version > 11,
            "the eleventh migration needs a schema version bump, or no installed " +
                "database will ever have it run against it",
        )
    }

    /** A database of that vintage: version 1, then every update up to it. */
    private fun databaseAtVersion(version: Long) = versionOneDatabase().also {
        if (version > 1) LukiaDatabase.Schema.migrate(it, oldVersion = 1, newVersion = version)
    }

    /** What a first run creates, which is what everything else has to match. */
    private fun currentDatabase() = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        .also { LukiaDatabase.Schema.create(it) }

    /**
     * Every table, column, foreign key and index the database has.
     *
     * Read through the pragmas rather than compared as `sqlite_master`'s
     * `CREATE` text, which is whatever was typed: a column added by `ALTER
     * TABLE` is appended verbatim, so a table migrated into the right shape and
     * one created in it are identical databases with different strings. The
     * pragmas answer about the schema rather than the source.
     *
     * Indexes SQLite makes for itself are left out: named after the table and
     * built from the primary key, they say nothing the columns have not.
     */
    private fun structureOf(driver: SqlDriver): List<String> {
        val tables = driver.rows(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name",
        )

        val columns = tables.flatMap { table ->
            driver.rows(
                """
                SELECT '$table.' || name || ' ' || type || ' notnull=' || "notnull" ||
                       ' default=' || COALESCE(dflt_value, '-') || ' pk=' || pk
                FROM pragma_table_info('$table')
                ORDER BY cid
                """.trimIndent(),
            )
        }

        val foreignKeys = tables.flatMap { table ->
            driver.rows(
                """
                SELECT '$table.' || "from" || ' -> ' || "table" || '.' || "to" ||
                       ' on_delete=' || on_delete
                FROM pragma_foreign_key_list('$table')
                ORDER BY id
                """.trimIndent(),
            )
        }

        val indexes = driver.rows(
            """
            SELECT name || ' ON ' || tbl_name || ' ' || COALESCE(sql, '')
            FROM sqlite_master
            WHERE type = 'index' AND sql IS NOT NULL
            ORDER BY name
            """.trimIndent(),
        )

        return columns + foreignKeys + indexes
    }

    private fun SqlDriver.rows(sql: String): List<String> = executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor: SqlCursor ->
            val rows = mutableListOf<String>()
            while (cursor.next().value) rows += cursor.getString(0).orEmpty()
            QueryResult.Value(rows.toList())
        },
        parameters = 0,
    ).value
}
