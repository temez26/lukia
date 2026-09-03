package io.lukia.core.infrastructure

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.lukia.core.domain.diagnostics.Diagnostics
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.core.infrastructure.diagnostics.recordingTo
import java.io.File
import java.util.Properties

/**
 * The library database, beside the desktop build's other stored state.
 *
 * The schema is created on a database that has none, which is what the first
 * run of the app finds. Later versions arrive as migrations; SQLDelight tracks
 * which have been applied through the file's own `user_version`.
 */
fun lukiaSqlDriver(
    /** Injectable so tests can work in a temporary directory. */
    file: File = File(lukiaAppDirectory(), DATABASE_FILE),
    diagnostics: Diagnostics = Diagnostics.None,
): SqlDriver {
    file.parentFile?.mkdirs()

    return JdbcSqliteDriver(
        url = "jdbc:sqlite:${file.absolutePath}",
        properties = Properties(),
        schema = LukiaDatabase.Schema.recordingTo(diagnostics),
    )
}

private const val DATABASE_FILE = "lukia.db"
