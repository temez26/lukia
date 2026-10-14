package io.lukia.server.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import java.util.Properties

/**
 * Opens, creating or migrating, the database in [dataDirectory].
 *
 * Connection settings are properties rather than pragmas for the reason the
 * app's own driver gives (`LukiaDriver.jvm.kt`): the driver opens a connection
 * per thread, and a pragma would hold on one of them only.
 */
fun openServerDatabase(dataDirectory: File): ServerDatabase {
    dataDirectory.mkdirs()

    val driver = JdbcSqliteDriver(
        url = "jdbc:sqlite:${File(dataDirectory, DatabaseFile).absolutePath}",
        properties = Properties().apply {
            setProperty("foreign_keys", "true")
            setProperty("journal_mode", "WAL")
            setProperty("busy_timeout", BusyTimeoutMillis.toString())
        },
        schema = ServerDatabase.Schema,
    )

    return ServerDatabase(driver)
}

private const val DatabaseFile = "lukia-server.db"

/** How long a writer waits for another to finish before failing; requests are short, so seconds is ample. */
private const val BusyTimeoutMillis = 5_000
