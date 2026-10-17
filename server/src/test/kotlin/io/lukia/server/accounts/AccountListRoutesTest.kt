package io.lukia.server.accounts

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.lukia.api.AccountSummary
import io.lukia.api.ApiErrorCodes
import io.lukia.api.ApiRoutes
import io.lukia.api.BookResponse
import io.lukia.api.BookStateRequest
import io.lukia.api.ChangePage
import io.lukia.api.FavoritePart
import io.lukia.api.ProgressPart
import io.lukia.api.SessionRequest
import io.lukia.api.TokenResponse
import io.lukia.server.serverTest
import io.lukia.server.setUpAdmin
import io.lukia.server.signUp
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountListRoutesTest {

    @Test
    fun `the admin sees every account with what it has read`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val alice = client.signUp(admin, "alice")
        client.upload(alice, "moby.txt", "Call me Ishmael.")
        client.upload(alice, "bartleby.txt", "I would prefer not to.")
        client.putState(alice, Finished, BookStateRequest(progress = ProgressPart(9, 10, 1)))
        client.putState(alice, Begun, BookStateRequest(progress = ProgressPart(0, 10, 1)))
        client.putState(alice, Liked, BookStateRequest(favorite = FavoritePart(true, 1)))
        client.putSession(alice, FirstSitting, SessionRequest(Begun, startedAt = 1_000, durationMs = 60_000))
        client.putSession(alice, SecondSitting, SessionRequest(Finished, startedAt = 100_000, durationMs = 30_000))

        val people = client.accounts(admin)

        assertEquals(
            listOf(
                AccountSummary("admin", isAdmin = true, createdAt = people[0].createdAt, 0, 0, 0, 0, 0, 0, null),
                AccountSummary(
                    username = "alice",
                    isAdmin = false,
                    createdAt = people[1].createdAt,
                    booksAdded = 2,
                    booksStarted = 2,
                    booksFinished = 1,
                    favourites = 1,
                    readingMs = 90_000,
                    sessions = 2,
                    lastReadAt = 130_000,
                ),
            ),
            people,
        )
    }

    @Test
    fun `only an admin lists or removes accounts`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val alice = client.signUp(admin, "alice")

        assertEquals(HttpStatusCode.Forbidden, client.get(ApiRoutes.Accounts) { bearerAuth(alice.accessToken) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.remove(alice, "admin").status)
    }

    @Test
    fun `a removed account is signed out and its books stay`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val alice = client.signUp(admin, "alice")
        client.upload(alice, "moby.txt", "Call me Ishmael.")

        val removed = client.remove(admin, "alice")

        assertEquals(HttpStatusCode.NoContent, removed.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiRoutes.Me) { bearerAuth(alice.accessToken) }.status)
        assertEquals(listOf("admin"), client.accounts(admin).map { it.username })
        val library: ChangePage<BookResponse> =
            client.get("${ApiRoutes.Books}?since=0") { bearerAuth(admin.accessToken) }.body()
        assertEquals(1, library.items.size)
    }

    @Test
    fun `the last admin cannot be removed, and nor can nobody`() = serverTest { client ->
        val admin = client.setUpAdmin()

        assertEquals(ApiErrorCodes.LastAdmin, client.remove(admin, "admin").errorCode())
        assertEquals(HttpStatusCode.NotFound, client.remove(admin, "nobody").status)
    }

    private suspend fun HttpClient.accounts(by: TokenResponse): List<AccountSummary> =
        get(ApiRoutes.Accounts) { bearerAuth(by.accessToken) }.body()

    private suspend fun HttpClient.remove(by: TokenResponse, username: String) =
        delete(ApiRoutes.account(username)) { bearerAuth(by.accessToken) }

    private suspend fun HttpClient.upload(by: TokenResponse, fileName: String, text: String) =
        post(ApiRoutes.Books) {
            parameter("fileName", fileName)
            bearerAuth(by.accessToken)
            contentType(ContentType.Application.OctetStream)
            setBody(text.toByteArray())
        }

    private suspend fun HttpClient.putState(by: TokenResponse, bookId: String, change: BookStateRequest) =
        put(ApiRoutes.myBook(bookId)) {
            bearerAuth(by.accessToken)
            contentType(ContentType.Application.Json)
            setBody(change)
        }

    private suspend fun HttpClient.putSession(by: TokenResponse, id: String, request: SessionRequest) =
        put(ApiRoutes.mySession(id)) {
            bearerAuth(by.accessToken)
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    private companion object {
        val Finished = "a".repeat(64)
        val Begun = "b".repeat(64)
        val Liked = "c".repeat(64)
        const val FirstSitting = "0b6f8c1e-6d7a-4c1b-9e1f-3a2b5c4d6e7f"
        const val SecondSitting = "1c7a9d2f-7e8b-4d2c-8f20-4b3c6d5e7f80"
    }
}
