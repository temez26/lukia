package io.lukia.features.account.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.lukia.api.AccountResponse
import io.lukia.api.ApiRoutes
import io.lukia.api.Credentials
import io.lukia.api.HealthResponse
import io.lukia.api.RedeemInviteRequest
import io.lukia.api.RefreshRequest
import io.lukia.api.TokenResponse
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

/** See [ServerApi]. Every failure becomes an [AccountFailure] - see [ServerHttp]. */
class KtorServerApi(private val http: ServerHttp) : ServerApi {

    constructor(client: HttpClient) : this(ServerHttp(client))

    override suspend fun health(address: String): ServerInfo =
        http.call<HealthResponse> { get(address + ApiRoutes.Health) }
            .let { ServerInfo(it.name, it.version, it.setupRequired, it.signUpOpen) }

    override suspend fun setUp(address: String, username: String, password: String) =
        tokens { postJson(address + ApiRoutes.Setup, Credentials(username, password)) }

    override suspend fun signIn(address: String, username: String, password: String) =
        tokens { postJson(address + ApiRoutes.SignIn, Credentials(username, password)) }

    override suspend fun signUp(address: String, username: String, password: String) =
        tokens { postJson(address + ApiRoutes.SignUp, Credentials(username, password)) }

    override suspend fun redeem(address: String, code: String, username: String, password: String) =
        tokens { postJson(address + ApiRoutes.Redeem, RedeemInviteRequest(code, username, password)) }

    override suspend fun refresh(address: String, refreshToken: String) =
        tokens { postJson(address + ApiRoutes.Refresh, RefreshRequest(refreshToken)) }

    override suspend fun signOut(address: String, accessToken: String) {
        http.send { post(address + ApiRoutes.SignOut) { bearerAuth(accessToken) } }
    }

    override suspend fun deleteAccount(address: String, accessToken: String) {
        http.send { delete(address + ApiRoutes.Me) { bearerAuth(accessToken) } }
    }

    override suspend fun me(address: String, accessToken: String): Pair<String, Boolean> =
        http.call<AccountResponse> { get(address + ApiRoutes.Me) { bearerAuth(accessToken) } }
            .let { it.username to it.isAdmin }

    private suspend inline fun <reified T> HttpClient.postJson(url: String, body: T): HttpResponse = post(url) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend inline fun tokens(crossinline request: suspend HttpClient.() -> HttpResponse): AuthTokens =
        http.call<TokenResponse> { request() }.let {
            AuthTokens(it.accessToken, it.accessExpiresAt, it.refreshToken, it.refreshExpiresAt)
        }
}
