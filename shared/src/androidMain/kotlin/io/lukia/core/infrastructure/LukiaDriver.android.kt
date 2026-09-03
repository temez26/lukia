package io.lukia.core.infrastructure

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.diagnostics.recordingTo

/**
 * Android's own SQLite, in app-private storage - removed with the app, and
 * backed up or not according to the manifest like every other private file.
 *
 * The schema is created on first run and migrated afterwards by the driver.
 */
fun lukiaSqlDriver(
    context: Context,
    diagnostics: Diagnostics = Diagnostics.None,
): SqlDriver = AndroidSqliteDriver(
    schema = LukiaDatabase.Schema.recordingTo(diagnostics),
    context = context,
    name = DATABASE_FILE,
)

private const val DATABASE_FILE = "lukia.db"
