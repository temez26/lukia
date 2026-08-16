package io.lukia.core.infrastructure

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.lukia.core.infrastructure.db.LukiaDatabase

/**
 * The database lands in Application Support, beside the working copies, for the
 * same reason they do: it is the app's own state rather than something the user
 * put there or should meet in the Files app.
 *
 * The schema is created on first run and migrated afterwards by the driver.
 */
fun lukiaSqlDriver(): SqlDriver = NativeSqliteDriver(
    schema = LukiaDatabase.Schema,
    name = DATABASE_FILE
)

private const val DATABASE_FILE = "lukia.db"
