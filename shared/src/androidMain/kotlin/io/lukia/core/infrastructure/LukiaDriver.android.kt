package io.lukia.core.infrastructure

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.diagnostics.failure
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.diagnostics.recordingTo

/**
 * Android's own SQLite, in app-private storage - removed with the app, and
 * backed up or not according to the manifest like every other private file.
 *
 * The schema is created on first run and migrated afterwards by the driver,
 * inside the transaction `SQLiteOpenHelper` opens around an upgrade - so a
 * migration that fails leaves the database at the version it was rather than
 * part way to a version no code knows how to finish.
 */
fun lukiaSqlDriver(
    context: Context,
    diagnostics: Diagnostics = Diagnostics.None,
): SqlDriver {
    val schema = LukiaDatabase.Schema.recordingTo(diagnostics)

    return AndroidSqliteDriver(
        schema = schema,
        context = context,
        name = DATABASE_FILE,
        callback = LukiaCallback(schema, diagnostics),
    )
}

/**
 * The schema, plus the two things the framework only asks about here.
 *
 * @see SupportSQLiteOpenHelper.Callback.onConfigure
 * @see SupportSQLiteOpenHelper.Callback.onCorruption
 */
private class LukiaCallback(
    schema: SqlSchema<QueryResult.Value<Unit>>,
    private val diagnostics: Diagnostics,
) : AndroidSqliteDriver.Callback(schema) {

    /**
     * Runs on the connection before any statement and before a migration, which
     * is the one place Android allows either of these to be asked for.
     *
     * Foreign keys are off by default in SQLite, so without this the
     * `ON DELETE CASCADE` on `reading_position` and `reader_layout` is a
     * comment - see `ReadingPosition.sq`. Write-ahead logging is what lets the
     * shelf be read while the sitting behind it is written, and is asked for
     * explicitly rather than left to the platform, whose default has changed
     * across the range of versions this app supports. `busy_timeout` is what
     * keeps a second write landing at once from failing outright - see
     * [BusyTimeoutMillis].
     */
    override fun onConfigure(db: SupportSQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
        db.execSQL("PRAGMA busy_timeout = $BusyTimeoutMillis")
    }

    /**
     * Recorded, then handled as the framework would have handled it - which is
     * to delete the file, so that the next open finds no database and makes one.
     *
     * Deleted rather than put aside as the desktop build does it. Android hands
     * this callback a database rather than a path, and app-private storage is
     * not somewhere a reader can reach a file to recover it from anyway, so a
     * kept copy would be bytes nobody could ever use. The record is what is
     * worth having: the reader is about to find their library empty, and the app
     * should at least know why.
     */
    override fun onCorruption(db: SupportSQLiteDatabase) {
        diagnostics.failure(
            area = DiagnosticArea.Storage,
            event = "database-unreadable",
            detail = "deleted",
        )

        super.onCorruption(db)
    }
}

private const val DATABASE_FILE = "lukia.db"
