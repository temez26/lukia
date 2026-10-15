package io.lukia.features.account

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.AccountStatus
import io.lukia.features.account.infrastructure.KtorServerApi
import io.lukia.features.account.infrastructure.serverHttpClient
import io.lukia.server.ServerConfig
import io.lukia.server.accounts.Argon2PasswordHasher
import io.lukia.server.db.ServerDatabase
import io.lukia.server.lukiaServer
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * The app's account code against the real server, over a real socket, with the
 * client the app ships. Everything else in this package tests one side against
 * a stand-in for the other; this is where the two halves of the wire have to
 * agree.
 */
class ServerContractTest {

    private val dataDirectory = Files.createTempDirectory("lukia-contract").toFile()
    private val server = startServer()
    private val address = "http://127.0.0.1:${runBlocking { server.engine.resolvedConnectors().first().port }}"
    private val session = AccountSession(KtorServerApi(serverHttpClient()), InMemoryAccountStore())

    @AfterTest
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 0)
        dataDirectory.deleteRecursively()
    }

    @Test
    fun aNewServerIsSetUpFromTheAppAndSignedOutOf() = runBlocking {
        val (checked, info) = session.check(address)
        assertEquals(true, info.setupRequired)
        assertEquals("Contract shelf", info.name)

        session.setUp(checked, "admin", "correct horse battery")
        val signedIn = assertIs<AccountStatus.SignedIn>(session.status.value)
        assertEquals("admin", signedIn.account.username)
        assertEquals(true, signedIn.account.isAdmin)

        val answer = session.authorized { at, token -> KtorServerApi(serverHttpClient()).me(at, token) }
        assertEquals("admin" to true, answer)

        session.signOut()
        assertEquals(AccountStatus.SignedOut, session.status.value)
    }

    @Test
    fun theServersRefusalsArriveAsTheProblemsTheAppNames() = runBlocking {
        session.setUp(address, "admin", "correct horse battery")
        session.signOut()

        assertEquals(AccountProblem.SignInRejected, problemOf { session.signIn(address, "admin", "wrong password") })
        assertEquals(
            AccountProblem.AlreadySetUp,
            problemOf {
                session.setUp(address, "other", "correct horse battery")
            },
        )
        assertEquals(
            AccountProblem.InvalidInvite,
            problemOf {
                session.redeem(address, "nope", "bob", "battery staple")
            },
        )
        assertEquals(AccountProblem.InvalidPassword, problemOf { session.redeem(address, "nope", "bob", "short") })
    }

    @Test
    fun anAddressWithNothingListeningIsUnreachable() = runBlocking {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 0)

        assertEquals(AccountProblem.ServerUnreachable, problemOf { session.check(address) })
    }

    private suspend fun problemOf(block: suspend () -> Unit): AccountProblem =
        assertFailsWith<AccountFailure> { block() }.problem

    private fun startServer(): EmbeddedServer<*, *> {
        val database = ServerDatabase(
            JdbcSqliteDriver(
                url = JdbcSqliteDriver.IN_MEMORY,
                properties = Properties().apply { setProperty("foreign_keys", "true") },
                schema = ServerDatabase.Schema,
            ),
        )
        val config = ServerConfig(name = "Contract shelf", port = 0, dataDirectory = dataDirectory)

        return embeddedServer(CIO, host = "127.0.0.1", port = 0) {
            lukiaServer(config, database, hasher = Argon2PasswordHasher(memoryKiB = 8, iterations = 1))
        }.start(wait = false)
    }
}
