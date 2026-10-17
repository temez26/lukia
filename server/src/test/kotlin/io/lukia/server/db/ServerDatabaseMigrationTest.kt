package io.lukia.server.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

/** That a server's database from before a schema change opens with nothing lost. */
class ServerDatabaseMigrationTest {

    /**
     * A 1.4.0 database is today's minus `server_setting`, at schema version 1.
     * Made by building today's and taking the table back out, which is that
     * database exactly while the migration only adds the one table.
     */
    @Test
    fun `a database from 1_4_0 gains the settings table and keeps its accounts`() {
        val directory = Files.createTempDirectory("lukia-migration").toFile()
        try {
            val url = "jdbc:sqlite:${directory.resolve("lukia-server.db").absolutePath}"
            JdbcSqliteDriver(url, schema = ServerDatabase.Schema).use { driver ->
                ServerDatabase(driver).accountQueries.insert("admin", "hash", true, 0)
                driver.execute(null, "DROP TABLE server_setting", 0)
                driver.execute(null, "PRAGMA user_version = 1", 0)
            }

            val upgraded = openServerDatabase(directory)

            assertEquals(1L, upgraded.accountQueries.countAdmins().executeAsOne())
            upgraded.serverSettingQueries.setValue("signUpOpen", "true")
            assertEquals("true", upgraded.serverSettingQueries.selectValue("signUpOpen").executeAsOne())
        } finally {
            directory.deleteRecursively()
        }
    }
}
