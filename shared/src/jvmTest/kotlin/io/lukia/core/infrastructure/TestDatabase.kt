package io.lukia.core.infrastructure

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.lukia.core.infrastructure.db.LukiaDatabase

/**
 * A database of its own, in memory, for one test.
 *
 * The real driver against real SQLite rather than a fake repository: what these
 * tests are actually about is the SQL - which statement preserves a favourite,
 * what a replace does to a shelf position - and a stand-in would answer those
 * questions with whatever the stand-in was written to believe.
 */
internal fun testDatabase(): LukiaDatabase = LukiaDatabase(
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, schema = LukiaDatabase.Schema),
)
