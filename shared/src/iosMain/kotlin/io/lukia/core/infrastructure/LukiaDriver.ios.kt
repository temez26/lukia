package io.lukia.core.infrastructure

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseFileContext
import co.touchlab.sqliter.JournalMode
import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.diagnostics.failure
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.diagnostics.recordingTo

/**
 * The database lands in Application Support, beside the working copies, for the
 * same reason they do: it is the app's own state rather than something the user
 * put there or should meet in the Files app.
 *
 * The schema is created on first run and migrated afterwards by the driver,
 * inside a transaction - so a migration that fails leaves the database at the
 * version it was.
 *
 * A file that cannot be read as a database at all is deleted rather than allowed
 * to fail every launch for ever. Deleted rather than put aside as the desktop
 * build does it: Application Support is not somewhere a reader can reach a file
 * from, so a kept copy would be bytes nobody could ever use.
 */
fun lukiaSqlDriver(
    diagnostics: Diagnostics = Diagnostics.None,
): SqlDriver = runCatching { openDatabase(diagnostics) }
    .recoverCatching { failure ->
        if (!failure.isDatabaseCorruption()) throw failure

        diagnostics.failure(
            area = DiagnosticArea.Storage,
            event = "database-unreadable",
            detail = "deleted",
            cause = failure,
        )

        DatabaseFileContext.deleteDatabase(DATABASE_FILE)
        openDatabase(diagnostics)
    }
    .getOrThrow()

private fun openDatabase(diagnostics: Diagnostics): SqlDriver = NativeSqliteDriver(
    schema = LukiaDatabase.Schema.recordingTo(diagnostics),
    name = DATABASE_FILE,
    onConfiguration = { configuration ->
        configuration.copy(
            // Readers and the writer stop blocking each other, which here is the
            // shelf being drawn while the sitting behind it is written.
            journalMode = JournalMode.WAL,
            extendedConfig = configuration.extendedConfig.copy(
                // Off by default in SQLite, so without this the
                // `ON DELETE CASCADE` on `reading_position` and `reader_layout`
                // is a comment - see `ReadingPosition.sq`.
                foreignKeyConstraints = true,
                busyTimeout = BusyTimeoutMillis,
            ),
        )
    },
)

private const val DATABASE_FILE = "lukia.db"
