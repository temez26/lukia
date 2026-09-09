package io.lukia.core.infrastructure

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.lukia.core.domain.diagnostics.DiagnosticArea
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.domain.diagnostics.failure
import io.lukia.core.domain.diagnostics.warn
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.diagnostics.recordingTo
import java.io.File
import java.util.Properties

/**
 * The library database, beside the desktop build's other stored state.
 *
 * The schema is created on a database that has none, which is what the first
 * run of the app finds. Later versions arrive as migrations; SQLDelight tracks
 * which have been applied through the file's own `user_version`, and runs the
 * whole run of them inside one transaction - so an update that fails leaves the
 * database at the version it was, rather than part way to a version no code
 * knows how to finish.
 *
 * A file that cannot be read as a database at all is put aside rather than
 * allowed to fail every launch for ever. See [quarantine].
 */
fun lukiaSqlDriver(
    /** Injectable so tests can work in a temporary directory. */
    file: File = File(lukiaAppDirectory(), DATABASE_FILE),
    diagnostics: Diagnostics = Diagnostics.None,
): SqlDriver {
    file.parentFile?.mkdirs()

    return runCatching { file.openDatabase(diagnostics) }
        .recoverCatching { failure ->
            if (!failure.isDatabaseCorruption()) throw failure

            file.quarantine(diagnostics, failure)
            file.openDatabase(diagnostics)
        }
        .getOrThrow()
}

private fun File.openDatabase(diagnostics: Diagnostics): SqlDriver = JdbcSqliteDriver(
    url = "jdbc:sqlite:$absolutePath",
    properties = connectionProperties(),
    schema = LukiaDatabase.Schema.recordingTo(diagnostics),
)

/**
 * How every connection to this database is opened.
 *
 * Properties rather than pragmas run once after opening, because this driver
 * opens a connection per thread that asks and closes it when the statement is
 * done. A pragma run against whichever connection happened to be first would
 * hold for that thread and no other - the worst of both, since the setting then
 * looks on and is off wherever it matters.
 *
 * `foreign_keys` is the one that changes behaviour rather than speed: SQLite has
 * it off by default, for the sake of databases written before 2009, and without
 * it the `ON DELETE CASCADE` on `reading_position` and `reader_layout` is a
 * comment. See `ReadingPosition.sq`.
 *
 * `journal_mode` belongs to the file rather than the connection, so it is set
 * once and stays set; naming it here puts a database restored from a backup
 * written in another mode back into this one. WAL lets the shelf be read while a
 * sitting is written, which is the ordinary case here: reading a book writes to
 * the same database the library screen reads.
 *
 * `busy_timeout` is the other half of that - see [BusyTimeoutMillis].
 */
private fun connectionProperties() = Properties().apply {
    setProperty("foreign_keys", "true")
    setProperty("journal_mode", "WAL")
    setProperty("busy_timeout", BusyTimeoutMillis.toString())
}

/**
 * Puts a database that cannot be read out of the way, so that the app opens an
 * empty one rather than refusing to start for as long as the file is there.
 *
 * Moved rather than deleted, and to one fixed name rather than a growing pile:
 * what is in it is the reader's library index, and a file unreadable to SQLite
 * is not necessarily unreadable to the recovery tools SQLite ships with.
 *
 * The sidecars go with it where they are still there. A write-ahead log and its
 * index belong to the database they were written beside, and one left next to a
 * new database of the same name is pages that database has never seen, which
 * SQLite would recover into it. SQLite removes a log it cannot use itself for
 * the commonest case - a file that is not a database at all - so most of the
 * time this finds nothing to move; what it covers is a header that reads over
 * pages that do not.
 */
private fun File.quarantine(diagnostics: Diagnostics, cause: Throwable) {
    diagnostics.failure(
        area = DiagnosticArea.Storage,
        event = "database-unreadable",
        detail = "put aside",
        cause = cause,
    )

    listOf(this to "db", sidecar("-wal") to "wal", sidecar("-shm") to "shm").forEach { (part, what) ->
        if (!part.exists()) return@forEach

        val aside = File(part.parentFile, part.name + QuarantineSuffix)

        // An earlier quarantine is overwritten. Two unreadable databases are not
        // twice the evidence, and `renameTo` will not replace a file on every
        // platform this desktop build runs on.
        aside.delete()

        if (!part.renameTo(aside) && !part.delete()) {
            // Worth knowing, because the retry above will now fail on the same
            // file and the reader will watch the app refuse to open.
            diagnostics.warn(
                area = DiagnosticArea.Storage,
                event = "database-quarantine-failed",
                detail = what,
            )
        }
    }
}

private fun File.sidecar(suffix: String) = File(parentFile, name + suffix)

private const val DATABASE_FILE = "lukia.db"
private const val QuarantineSuffix = ".unreadable"
