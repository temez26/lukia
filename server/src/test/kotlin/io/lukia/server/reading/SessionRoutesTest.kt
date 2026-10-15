package io.lukia.server.reading

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
import io.lukia.api.ApiRoutes
import io.lukia.api.ChangePage
import io.lukia.api.SessionRequest
import io.lukia.api.SessionResponse
import io.lukia.api.TokenResponse
import io.lukia.server.serverTest
import io.lukia.server.setUpAdmin
import io.lukia.server.signUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

class SessionRoutesTest {

    private val sitting = "0b6f8c1e-6d7a-4c1b-9e1f-3a2b5c4d6e7f"

    @Test
    fun `a session is recorded under its own id`() = serverTest { client ->
        val admin = client.setUpAdmin()

        val stored: SessionResponse =
            client.putSession(admin, sitting, SessionRequest(MobyDick, 1_000, 60_000, "Moby Dick")).body()

        assertEquals(SessionResponse(sitting, MobyDick, 1_000, 60_000, "Moby Dick"), stored)
        assertEquals(listOf(stored), client.sessionChanges(admin).items)
    }

    @Test
    fun `a session sent again grows but never shrinks or moves`() = serverTest { client ->
        val admin = client.setUpAdmin()
        client.putSession(admin, sitting, SessionRequest(MobyDick, 1_000, 60_000, "Moby Dick"))

        client.putSession(admin, sitting, SessionRequest(MobyDick, 1_000, 300_000, "Moby Dick"))
        val retried: SessionResponse =
            client.putSession(admin, sitting, SessionRequest(MobyDick, 9_999, 120_000, "Another title")).body()

        assertEquals(SessionResponse(sitting, MobyDick, 1_000, 300_000, "Moby Dick"), retried)
        assertEquals(1, client.sessionChanges(admin).items.size)
    }

    @Test
    fun `a session sent again unchanged is not a new change`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val request = SessionRequest(MobyDick, 1_000, 60_000)
        client.putSession(admin, sitting, request)
        val seen = client.sessionChanges(admin).cursor

        client.putSession(admin, sitting, request)

        assertTrue(client.sessionChanges(admin, since = seen).items.isEmpty())
    }

    @Test
    fun `two accounts' sessions never meet, even under one id`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val alice = client.signUp(admin, "alice")
        val bob = client.signUp(admin, "bob")

        client.putSession(alice, sitting, SessionRequest(MobyDick, 1_000, 60_000))
        client.putSession(bob, sitting, SessionRequest(MobyDick, 5_000, 1_000))

        assertEquals(listOf(60_000L), client.sessionChanges(alice).items.map { it.durationMs })
        assertEquals(listOf(1_000L), client.sessionChanges(bob).items.map { it.durationMs })
    }

    @Test
    fun `nonsense is refused`() = serverTest { client ->
        val admin = client.setUpAdmin()

        assertEquals(
            HttpStatusCode.BadRequest,
            client.putSession(admin, "not-a-uuid", SessionRequest(MobyDick, 1_000, 60_000)).status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            client.putSession(admin, sitting, SessionRequest("not-a-hash", 1_000, 60_000)).status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            client.putSession(admin, sitting, SessionRequest(MobyDick, 1_000, 25.hours.inWholeMilliseconds)).status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            client.putSession(admin, sitting, SessionRequest(MobyDick, 1_000, -1)).status,
        )
    }

    @Test
    fun `sessions need a signed-in account`() = serverTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiRoutes.MySessions).status)
    }

    private suspend fun HttpClient.putSession(by: TokenResponse, id: String, request: SessionRequest): HttpResponse =
        put(ApiRoutes.mySession(id)) {
            bearerAuth(by.accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    private suspend fun HttpClient.sessionChanges(by: TokenResponse, since: Long = 0): ChangePage<SessionResponse> =
        get("${ApiRoutes.MySessions}?since=$since") { bearerAuth(by.accessToken) }.body()
}
