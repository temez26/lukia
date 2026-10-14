package io.lukia.server

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.lukia.api.ApiRoutes
import io.lukia.api.Credentials
import io.lukia.api.InviteResponse
import io.lukia.api.RedeemInviteRequest
import io.lukia.api.TokenResponse
import io.lukia.server.accounts.Argon2PasswordHasher
import io.lukia.server.db.ServerDatabase
import java.nio.file.Files
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Properties
import kotlin.time.Duration

/** A database that lives as long as the test, with the same schema and foreign keys as the real one. */
fun inMemoryDatabase(): ServerDatabase = ServerDatabase(
    JdbcSqliteDriver(
        url = JdbcSqliteDriver.IN_MEMORY,
        properties = Properties().apply { setProperty("foreign_keys", "true") },
        schema = ServerDatabase.Schema,
    ),
)

/** A configuration whose data directory is a new temporary one, which [serverTest] removes afterwards. */
fun testConfig(maxUploadBytes: Long = ServerConfig.DefaultMaxUploadBytes) = ServerConfig(
    name = "Test shelf",
    port = 0,
    dataDirectory = Files.createTempDirectory("lukia-server-test").toFile(),
    maxUploadBytes = maxUploadBytes,
)

/** Real Argon2id at the smallest cost it accepts, so a test hashes in microseconds rather than tens of milliseconds. */
val CheapHasher = Argon2PasswordHasher(memoryKiB = 8, iterations = 1)

/** A clock that only moves when a test moves it. */
class TestClock(private var now: Instant = Instant.parse("2026-10-14T12:00:00Z")) : Clock() {
    fun advance(by: Duration) {
        now = now.plusMillis(by.inWholeMilliseconds)
    }

    override fun instant(): Instant = now

    override fun getZone() = ZoneOffset.UTC

    override fun withZone(zone: java.time.ZoneId?) = this
}

/** Runs [block] against the whole server, with a client that speaks the same JSON. */
fun serverTest(
    database: ServerDatabase = inMemoryDatabase(),
    clock: Clock = TestClock(),
    config: ServerConfig = testConfig(),
    block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit,
) = try {
    testApplication {
        application { lukiaServer(config, database, clock, CheapHasher) }

        val client = createClient {
            install(ContentNegotiation) { json() }
        }

        block(client)
    }
} finally {
    config.dataDirectory.deleteRecursively()
}

suspend inline fun <reified T : Any> HttpClient.postJson(
    path: String,
    body: T,
    accessToken: String? = null,
): HttpResponse = post(path) {
    contentType(ContentType.Application.Json)
    setBody(body)
    accessToken?.let(::bearerAuth)
}

suspend fun HttpClient.postEmpty(path: String, accessToken: String): HttpResponse =
    post(path) { bearerAuth(accessToken) }

const val AdminPassword = "correct horse battery"

/** Sets the server up with an admin called `admin` and returns its tokens. */
suspend fun HttpClient.setUpAdmin(): TokenResponse =
    postJson(ApiRoutes.Setup, Credentials("admin", AdminPassword)).body()

/** Invites and signs up [username] through [admin], returning the new account's tokens. */
suspend fun HttpClient.signUp(admin: TokenResponse, username: String, password: String = AdminPassword): TokenResponse {
    val invite: InviteResponse = postEmpty(ApiRoutes.Invites, admin.accessToken).body()
    return postJson(ApiRoutes.Redeem, RedeemInviteRequest(invite.code, username, password)).body()
}
