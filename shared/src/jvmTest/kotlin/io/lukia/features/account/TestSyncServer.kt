package io.lukia.features.account

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.path
import io.lukia.api.ApiRoutes
import io.lukia.features.account.infrastructure.serverHttpClient
import io.lukia.server.ServerConfig
import io.lukia.server.accounts.Argon2PasswordHasher
import io.lukia.server.db.ServerDatabase
import io.lukia.server.lukiaServer
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.Collections
import java.util.Properties

/**
 * The real sync server on a real port, for tests of the app's half of the wire.
 * Its data lives in a temporary directory that [stop] removes.
 */
class TestSyncServer : AutoCloseable {

    private val dataDirectory = Files.createTempDirectory("lukia-test-server").toFile()

    private val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val server = embeddedServer(CIO, host = "127.0.0.1", port = 0) {
        lukiaServer(
            ServerConfig(name = "Contract shelf", port = 0, dataDirectory = dataDirectory),
            ServerDatabase(
                JdbcSqliteDriver(
                    url = JdbcSqliteDriver.IN_MEMORY,
                    properties = Properties().apply { setProperty("foreign_keys", "true") },
                    schema = ServerDatabase.Schema,
                ),
            ),
            hasher = Argon2PasswordHasher(memoryKiB = 8, iterations = 1),
        )
        intercept(ApplicationCallPipeline.Monitoring) { requests += call.request.path() }
    }.start(wait = false)

    /** How many requests have reached [path] - a round asks for its book states exactly once. */
    fun requestsTo(path: String): Int = synchronized(requests) { requests.count { it == path } }

    val address = "http://127.0.0.1:${runBlocking { server.engine.resolvedConnectors().first().port }}"

    /** Uploads a book as the account [accessToken] belongs to, the way the admin page does. */
    suspend fun upload(accessToken: String, fileName: String, bytes: ByteArray) {
        serverHttpClient().post(address + ApiRoutes.Books) {
            parameter("fileName", fileName)
            bearerAuth(accessToken)
            setBody(bytes)
        }
    }

    suspend fun remove(accessToken: String, id: String) {
        serverHttpClient().delete(address + ApiRoutes.book(id)) { bearerAuth(accessToken) }
    }

    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 0)
    }

    override fun close() {
        stop()
        dataDirectory.deleteRecursively()
    }
}
