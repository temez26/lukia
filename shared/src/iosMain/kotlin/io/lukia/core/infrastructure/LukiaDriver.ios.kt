package io.lukia.core.infrastructure

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.diagnostics.recordingTo

/**
 * The database lands in Application Support, beside the working copies, for the
 * same reason they do: it is the app's own state rather than something the user
 * put there or should meet in the Files app.
 *
 * The schema is created on first run and migrated afterwards by the driver.
 */
fun lukiaSqlDriver(
    diagnostics: Diagnostics = Diagnostics.None,
): SqlDriver = NativeSqliteDriver(
    schema = LukiaDatabase.Schema.recordingTo(diagnostics),
    name = DATABASE_FILE,
)

private const val DATABASE_FILE = "lukia.db"
