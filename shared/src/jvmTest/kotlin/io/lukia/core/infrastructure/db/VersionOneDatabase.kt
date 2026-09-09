package io.lukia.core.infrastructure.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

/**
 * The schema as it stood at version 1, written out rather than generated.
 *
 * It has to be: there is no `0.sqm`, so nothing in the build can replay this
 * app's history - the only initial schema SQLDelight knows how to create is the
 * current one. The oldest database still in the world exists here or nowhere,
 * and every test about what an update does to an existing library starts from
 * it.
 *
 * Later versions are made by migrating this rather than writing them out again -
 * less to keep in step, and more honest: a version-6 database in the world is a
 * version-1 database that has had five updates.
 */
private val VersionOneSchema = """
    CREATE TABLE document (
        location TEXT NOT NULL PRIMARY KEY,
        fileName TEXT NOT NULL,
        extension TEXT NOT NULL,
        type TEXT NOT NULL,
        fileExists INTEGER NOT NULL,
        sizeBytes INTEGER NOT NULL,
        isFavorite INTEGER NOT NULL DEFAULT 0,
        coverImage BLOB,
        sortKey INTEGER NOT NULL
    );
    CREATE TABLE reading_position (
        location TEXT NOT NULL PRIMARY KEY,
        pageIndex INTEGER NOT NULL,
        pageCount INTEGER NOT NULL
    );
""".trimIndent()

/**
 * An empty database of that vintage, stamped as version 1 - which is what tells
 * the driver there is a migration to run.
 */
internal fun versionOneDatabase(): JdbcSqliteDriver {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)

    VersionOneSchema.split(';')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .forEach { driver.execute(null, it, 0) }

    driver.execute(null, "PRAGMA user_version = 1", 0)

    return driver
}
