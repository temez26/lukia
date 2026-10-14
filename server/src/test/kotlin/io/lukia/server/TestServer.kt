package io.lukia.server

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.lukia.server.db.ServerDatabase
import java.io.File
import java.util.Properties

/** A database that lives as long as the test, with the same schema and foreign keys as the real one. */
fun inMemoryDatabase(): ServerDatabase = ServerDatabase(
    JdbcSqliteDriver(
        url = JdbcSqliteDriver.IN_MEMORY,
        properties = Properties().apply { setProperty("foreign_keys", "true") },
        schema = ServerDatabase.Schema,
    ),
)

val TestConfig = ServerConfig(name = "Test shelf", port = 0, dataDirectory = File("unused"))

/** Runs [block] against the whole server, with a client that speaks the same JSON. */
fun serverTest(
    database: ServerDatabase = inMemoryDatabase(),
    block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit,
) = testApplication {
    application { lukiaServer(TestConfig, database) }

    val client = createClient {
        install(ContentNegotiation) { json() }
    }

    block(client)
}
