package io.lukia.server.accounts

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
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
import io.lukia.api.RefreshRequest
import io.lukia.api.TokenResponse
import io.lukia.server.AdminPassword
import io.lukia.server.TestClock
import io.lukia.server.postEmpty
import io.lukia.server.postJson
import io.lukia.server.serverTest
import io.lukia.server.setUpAdmin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class SignInRoutesTest {

    @Test
    fun `setup makes the first account an admin and signs it in`() = serverTest { client ->
        val response = client.postJson(ApiRoutes.Setup, Credentials("admin", AdminPassword))

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(AccountResponse("admin", isAdmin = true), client.me(response.body<TokenResponse>()).body())
    }

    @Test
    fun `setup is refused once an account exists`() = serverTest { client ->
        client.setUpAdmin()

        val response = client.postJson(ApiRoutes.Setup, Credentials("intruder", AdminPassword))

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals(ApiErrorCodes.SetupDone, response.errorCode())
    }

    @Test
    fun `a username or password outside the rules is refused`() = serverTest { client ->
        val badName = client.postJson(ApiRoutes.Setup, Credentials("Admin!", AdminPassword))
        val shortPassword = client.postJson(ApiRoutes.Setup, Credentials("admin", "short"))

        assertEquals(ApiErrorCodes.InvalidUsername, badName.errorCode())
        assertEquals(ApiErrorCodes.InvalidPassword, shortPassword.errorCode())
    }

    @Test
    fun `signing in with the right password returns working tokens`() = serverTest { client ->
        client.setUpAdmin()

        val response = client.postJson(ApiRoutes.SignIn, Credentials("admin", AdminPassword))

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(HttpStatusCode.OK, client.me(response.body()).status)
    }

    @Test
    fun `a wrong password and an unknown username are refused alike`() = serverTest { client ->
        client.setUpAdmin()

        val wrongPassword = client.postJson(ApiRoutes.SignIn, Credentials("admin", "not the password"))
        val unknownUser = client.postJson(ApiRoutes.SignIn, Credentials("nobody", AdminPassword))

        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals(wrongPassword.body<ApiError>(), unknownUser.body<ApiError>())
    }

    @Test
    fun `repeated failures lock the username until the window passes`() {
        val clock = TestClock()

        serverTest(clock = clock) { client ->
            client.setUpAdmin()
            repeat(10) { client.postJson(ApiRoutes.SignIn, Credentials("admin", "not the password")) }

            val locked = client.postJson(ApiRoutes.SignIn, Credentials("admin", AdminPassword))
            clock.advance(15.minutes)
            val afterWindow = client.postJson(ApiRoutes.SignIn, Credentials("admin", AdminPassword))

            assertEquals(HttpStatusCode.TooManyRequests, locked.status)
            assertEquals(HttpStatusCode.OK, afterWindow.status)
        }
    }

    @Test
    fun `a request without a valid token is unauthorized`() = serverTest { client ->
        val noToken = client.get(ApiRoutes.Me)
        val madeUp = client.get(ApiRoutes.Me) { bearerAuth("made-up") }

        assertEquals(HttpStatusCode.Unauthorized, noToken.status)
        assertEquals(ApiErrorCodes.Unauthorized, madeUp.errorCode())
    }

    @Test
    fun `an access token expires and a refresh replaces it`() {
        val clock = TestClock()

        serverTest(clock = clock) { client ->
            val tokens = client.setUpAdmin()
            clock.advance(16.minutes)

            val expired = client.me(tokens)
            val refreshed: TokenResponse =
                client.postJson(ApiRoutes.Refresh, RefreshRequest(tokens.refreshToken)).body()

            assertEquals(HttpStatusCode.Unauthorized, expired.status)
            assertEquals(HttpStatusCode.OK, client.me(refreshed).status)
            assertNotEquals(tokens.refreshToken, refreshed.refreshToken)
        }
    }

    @Test
    fun `a refresh token used twice ends the whole sign-in`() = serverTest { client ->
        val tokens = client.setUpAdmin()
        val refreshed: TokenResponse = client.postJson(ApiRoutes.Refresh, RefreshRequest(tokens.refreshToken)).body()

        val replayed = client.postJson(ApiRoutes.Refresh, RefreshRequest(tokens.refreshToken))

        assertEquals(HttpStatusCode.Unauthorized, replayed.status)
        assertEquals(HttpStatusCode.Unauthorized, client.me(refreshed).status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.postJson(ApiRoutes.Refresh, RefreshRequest(refreshed.refreshToken)).status,
        )
    }

    @Test
    fun `a refresh token stops working after thirty days`() {
        val clock = TestClock()

        serverTest(clock = clock) { client ->
            val tokens = client.setUpAdmin()
            clock.advance(31.days)

            assertEquals(
                HttpStatusCode.Unauthorized,
                client.postJson(ApiRoutes.Refresh, RefreshRequest(tokens.refreshToken)).status,
            )
        }
    }

    @Test
    fun `signing out ends that sign-in and no other`() = serverTest { client ->
        val phone = client.setUpAdmin()
        val desktop: TokenResponse = client.postJson(ApiRoutes.SignIn, Credentials("admin", AdminPassword)).body()

        assertEquals(HttpStatusCode.NoContent, client.postEmpty(ApiRoutes.SignOut, phone.accessToken).status)

        assertEquals(HttpStatusCode.Unauthorized, client.me(phone).status)
        assertEquals(
            HttpStatusCode.Unauthorized,
            client.postJson(ApiRoutes.Refresh, RefreshRequest(phone.refreshToken)).status,
        )
        assertEquals(HttpStatusCode.OK, client.me(desktop).status)
    }

    @Test
    fun `a body that is not the expected JSON is a bad request`() = serverTest { client ->
        val response = client.post(ApiRoutes.SignIn) {
            contentType(ContentType.Application.Json)
            setBody("""{"username": 3}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ApiErrorCodes.BadRequest, response.errorCode())
    }
}

suspend fun HttpClient.me(tokens: TokenResponse): HttpResponse = get(ApiRoutes.Me) { bearerAuth(tokens.accessToken) }

suspend fun HttpResponse.errorCode(): String = body<ApiError>().code
