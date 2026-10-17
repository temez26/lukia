package io.lukia.server.accounts

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.lukia.api.AccountResponse
import io.lukia.api.ApiError
import io.lukia.api.ApiErrorCodes
import io.lukia.api.ApiRoutes
import io.lukia.api.Credentials
import io.lukia.api.HealthResponse
import io.lukia.api.ServerSettings
import io.lukia.api.TokenResponse
import io.lukia.server.AdminPassword
import io.lukia.server.postJson
import io.lukia.server.serverTest
import io.lukia.server.setUpAdmin
import io.lukia.server.signUp
import kotlin.test.Test
import kotlin.test.assertEquals

/** Creating an account from the app, which the admin opens and closes. */
class SignUpRoutesTest {

    private suspend fun HttpClient.setSignUp(open: Boolean, accessToken: String): HttpResponse =
        put(ApiRoutes.Settings) {
            contentType(ContentType.Application.Json)
            setBody(ServerSettings(signUpOpen = open))
            bearerAuth(accessToken)
        }

    private suspend fun HttpClient.me(tokens: TokenResponse): AccountResponse =
        get(ApiRoutes.Me) { bearerAuth(tokens.accessToken) }.body()

    private suspend fun HttpResponse.errorCode(): String = body<ApiError>().code

    /** A server is often on the open internet: nobody can join until the admin says so. */
    @Test
    fun `sign-up is closed on a new server and creates nothing`() = serverTest { client ->
        val admin = client.setUpAdmin()

        val response = client.postJson(ApiRoutes.SignUp, Credentials("alice", AdminPassword))
        val signIn = client.postJson(ApiRoutes.SignIn, Credentials("alice", AdminPassword))

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals(ApiErrorCodes.SignUpClosed, response.errorCode())
        assertEquals(HttpStatusCode.Unauthorized, signIn.status)
        assertEquals(false, client.get(ApiRoutes.Health).body<HealthResponse>().signUpOpen)
        assertEquals(
            ServerSettings(signUpOpen = false),
            client.get(ApiRoutes.Settings) {
                bearerAuth(admin.accessToken)
            }.body(),
        )
    }

    @Test
    fun `once the admin opens it, anyone can make an ordinary account`() = serverTest { client ->
        val admin = client.setUpAdmin()
        client.setSignUp(open = true, admin.accessToken)

        val response = client.postJson(ApiRoutes.SignUp, Credentials("alice", AdminPassword))

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(AccountResponse("alice", isAdmin = false), client.me(response.body()))
        assertEquals(true, client.get(ApiRoutes.Health).body<HealthResponse>().signUpOpen)
    }

    @Test
    fun `closing it again stops new accounts`() = serverTest { client ->
        val admin = client.setUpAdmin()
        client.setSignUp(open = true, admin.accessToken)
        client.setSignUp(open = false, admin.accessToken)

        val response = client.postJson(ApiRoutes.SignUp, Credentials("alice", AdminPassword))

        assertEquals(ApiErrorCodes.SignUpClosed, response.errorCode())
    }

    @Test
    fun `only an admin can see or change the setting`() = serverTest { client ->
        val alice = client.signUp(client.setUpAdmin(), "alice")

        val read = client.get(ApiRoutes.Settings) { bearerAuth(alice.accessToken) }
        val change = client.setSignUp(open = true, alice.accessToken)
        val anonymous = client.get(ApiRoutes.Settings)

        assertEquals(HttpStatusCode.Forbidden, read.status)
        assertEquals(HttpStatusCode.Forbidden, change.status)
        assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
    }

    @Test
    fun `sign-up keeps the rules every other way in has`() = serverTest { client ->
        val admin = client.setUpAdmin()
        client.setSignUp(open = true, admin.accessToken)

        val taken = client.postJson(ApiRoutes.SignUp, Credentials("admin", AdminPassword))
        val badName = client.postJson(ApiRoutes.SignUp, Credentials("A!", AdminPassword))
        val shortPassword = client.postJson(ApiRoutes.SignUp, Credentials("alice", "short"))

        assertEquals(ApiErrorCodes.UsernameTaken, taken.errorCode())
        assertEquals(ApiErrorCodes.InvalidUsername, badName.errorCode())
        assertEquals(ApiErrorCodes.InvalidPassword, shortPassword.errorCode())
    }
}
