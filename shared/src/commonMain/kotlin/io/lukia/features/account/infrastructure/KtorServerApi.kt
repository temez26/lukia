package io.lukia.features.account.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.lukia.api.AccountResponse
import io.lukia.api.ApiError
import io.lukia.api.ApiErrorCodes
import io.lukia.api.ApiRoutes
import io.lukia.api.Credentials
import io.lukia.api.HealthResponse
import io.lukia.api.RedeemInviteRequest
import io.lukia.api.RefreshRequest
import io.lukia.api.TokenResponse
import io.lukia.core.domain.runCatchingApp
import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem
import io.lukia.features.account.domain.AuthTokens
import io.lukia.features.account.domain.ServerApi
import io.lukia.features.account.domain.ServerInfo
import kotlinx.serialization.json.Json

/**
 * The client the sync server is spoken to with.
 *
 * Redirects are not followed, for the translator's reason: the address was
 * checked before anything was sent (see `serverAddress`), and a redirect is
 * somebody else choosing a host nothing has checked - with a password in the
 * body. `ignoreUnknownKeys` so a newer server's added fields do not break an
 * older app; see docs/decisions/0004-shared-api-module.md.
 */
fun serverHttpClient(): HttpClient = HttpClient { configureForServer() }

/** The same client over [engine] - a test's stand-in for the network. */
fun serverHttpClient(engine: HttpClientEngine): HttpClient = HttpClient(engine) { configureForServer() }

private fun HttpClientConfig<*>.configureForServer() {
    expectSuccess = false
    followRedirects = false

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(HttpTimeout) {
        connectTimeoutMillis = TimeoutMillis
        socketTimeoutMillis = TimeoutMillis
        requestTimeoutMillis = TimeoutMillis
    }
}

/**
 * Long enough for a sign-in, which is a deliberately slow password hash on a
 * home server; short enough that a wrong address is reported while the reader
 * is still looking at the form.
 */
private const val TimeoutMillis = 15_000L

/** See [ServerApi]. Every failure becomes an [AccountFailure]; nothing else escapes. */
class KtorServerApi(private val client: HttpClient) : ServerApi {

    override suspend fun health(address: String): ServerInfo =
        call<HealthResponse> { client.get(address + ApiRoutes.Health) }
            .let { ServerInfo(it.name, it.version, it.setupRequired) }

    override suspend fun setUp(address: String, username: String, password: String) =
        tokens { postJson(address + ApiRoutes.Setup, Credentials(username, password)) }

    override suspend fun signIn(address: String, username: String, password: String) =
        tokens { postJson(address + ApiRoutes.SignIn, Credentials(username, password)) }

    override suspend fun redeem(address: String, code: String, username: String, password: String) =
        tokens { postJson(address + ApiRoutes.Redeem, RedeemInviteRequest(code, username, password)) }

    override suspend fun refresh(address: String, refreshToken: String) =
        tokens { postJson(address + ApiRoutes.Refresh, RefreshRequest(refreshToken)) }

    override suspend fun signOut(address: String, accessToken: String) {
        send { client.post(address + ApiRoutes.SignOut) { bearerAuth(accessToken) } }
    }

    override suspend fun me(address: String, accessToken: String): Pair<String, Boolean> =
        call<AccountResponse> { client.get(address + ApiRoutes.Me) { bearerAuth(accessToken) } }
            .let { it.username to it.isAdmin }

    private suspend inline fun <reified T> postJson(url: String, body: T): HttpResponse = client.post(url) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend inline fun tokens(crossinline request: suspend () -> HttpResponse): AuthTokens =
        call<TokenResponse> { request() }.let {
            AuthTokens(it.accessToken, it.accessExpiresAt, it.refreshToken, it.refreshExpiresAt)
        }

    /** A request whose answer is a [T]. Something that answers in another shape is not a Lukia server. */
    private suspend inline fun <reified T> call(crossinline request: suspend () -> HttpResponse): T {
        val response = send { request() }
        return runCatchingApp { response.body<T>() }
            .getOrElse { throw AccountFailure(AccountProblem.NotALukiaServer) }
    }

    /** A request that succeeded, or the [AccountFailure] that says why not. */
    private suspend inline fun send(crossinline request: suspend () -> HttpResponse): HttpResponse {
        val response = runCatchingApp { request() }
            .getOrElse { throw AccountFailure(AccountProblem.ServerUnreachable) }

        if (response.status.isSuccess()) return response
        throw AccountFailure(problemIn(response))
    }

    /** What a failed response says went wrong. One that does not say it the server's way is not the server. */
    private suspend fun problemIn(response: HttpResponse): AccountProblem =
        runCatchingApp { response.body<ApiError>() }
            .map { problemFor(it.code) }
            .getOrDefault(AccountProblem.NotALukiaServer)

    private companion object {
        fun problemFor(code: String): AccountProblem = when (code) {
            ApiErrorCodes.Unauthorized -> AccountProblem.SessionExpired
            ApiErrorCodes.InvalidCredentials -> AccountProblem.SignInRejected
            ApiErrorCodes.TooManyAttempts -> AccountProblem.TooManyAttempts
            ApiErrorCodes.InvalidUsername -> AccountProblem.InvalidUsername
            ApiErrorCodes.InvalidPassword -> AccountProblem.InvalidPassword
            ApiErrorCodes.UsernameTaken -> AccountProblem.UsernameTaken
            ApiErrorCodes.InvalidInvite -> AccountProblem.InvalidInvite
            ApiErrorCodes.SetupDone -> AccountProblem.AlreadySetUp
            else -> AccountProblem.ServerFailed
        }
    }
}
