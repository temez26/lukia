package io.lukia.features.account

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.lukia.api.ApiRoutes
import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.AuthTokens
import io.lukia.features.account.domain.ServerInfo
import io.lukia.features.account.infrastructure.KtorServerApi
import io.lukia.features.account.infrastructure.serverHttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** The app's half of the wire: what it sends, and what it makes of each answer the server can give. */
class KtorServerApiTest {

    @Test
    fun healthIsReadAndExtraFieldsFromANewerServerAreIgnored() = runTest {
        val api = apiAnswering { json("""{"name":"Home shelf","version":"1.2.0","setupRequired":true,"motd":"hi"}""") }

        assertEquals(ServerInfo("Home shelf", "1.2.0", setupRequired = true), api.health(HomeServer))
    }

    @Test
    fun aNewerServerSaysWhetherItTakesNewAccounts() = runTest {
        val api = apiAnswering {
            json("""{"name":"Home shelf","version":"1.5.0","setupRequired":false,"signUpOpen":true}""")
        }

        assertEquals(true, api.health(HomeServer).signUpOpen)
    }

    @Test
    fun signingInSendsTheCredentialsAndReadsTheTokens() = runTest {
        var sent: HttpRequestData? = null
        val api = apiAnswering { request ->
            sent = request
            json("""{"accessToken":"a","accessExpiresAt":1,"refreshToken":"r","refreshExpiresAt":2}""")
        }

        val tokens = api.signIn(HomeServer, "alice", "correct horse")

        assertEquals(AuthTokens("a", 1, "r", 2), tokens)
        assertEquals("$HomeServer${ApiRoutes.SignIn}", sent?.url.toString())
        val body = sent!!.body.toByteArray().decodeToString()
        assertTrue("\"username\":\"alice\"" in body && "\"password\":\"correct horse\"" in body, body)
    }

    @Test
    fun theServersErrorCodesBecomeProblemsTheReaderCanActOn() = runTest {
        val cases = mapOf(
            "invalid_credentials" to AccountProblem.SignInRejected,
            "too_many_attempts" to AccountProblem.TooManyAttempts,
            "username_taken" to AccountProblem.UsernameTaken,
            "invalid_invite" to AccountProblem.InvalidInvite,
            "sign_up_closed" to AccountProblem.SignUpClosed,
            "setup_done" to AccountProblem.AlreadySetUp,
            "unauthorized" to AccountProblem.SessionExpired,
            "something_new" to AccountProblem.ServerFailed,
        )

        for ((code, problem) in cases) {
            val api = apiAnswering { json("""{"code":"$code","message":"x"}""", HttpStatusCode.BadRequest) }

            assertEquals(problem, problemOf { api.signIn(HomeServer, "alice", "x") }, code)
        }
    }

    @Test
    fun somethingThatIsNotALukiaServerIsSaidToBeSo() = runTest {
        val webPage =
            apiAnswering {
                respond("<html></html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
            }
        val notFound = apiAnswering { respondError(HttpStatusCode.NotFound, "<html>404</html>") }

        assertEquals(AccountProblem.NotALukiaServer, problemOf { webPage.health(HomeServer) })
        assertEquals(AccountProblem.NotALukiaServer, problemOf { notFound.health(HomeServer) })
    }

    @Test
    fun aServerThatCannotBeReachedIsSaidToBeSo() = runTest {
        val api = apiAnswering { throw IllegalStateException("connection refused") }

        assertEquals(AccountProblem.ServerUnreachable, problemOf { api.health(HomeServer) })
    }

    @Test
    fun aRedirectIsNotFollowed() = runTest {
        val api = apiAnswering {
            respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://elsewhere.example.com"))
        }

        assertEquals(AccountProblem.NotALukiaServer, problemOf { api.signIn(HomeServer, "alice", "x") })
    }

    private fun apiAnswering(answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        KtorServerApi(serverHttpClient(MockEngine { request -> answer(request) }))

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))

    private suspend fun problemOf(block: suspend () -> Unit): AccountProblem =
        assertFailsWith<AccountFailure> { block() }.problem
}
