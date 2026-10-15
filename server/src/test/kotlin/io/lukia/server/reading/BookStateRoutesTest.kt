package io.lukia.server.reading

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.lukia.api.ApiRoutes
import io.lukia.api.BookStateRequest
import io.lukia.api.BookStateResponse
import io.lukia.api.ChangePage
import io.lukia.api.FavoritePart
import io.lukia.api.LayoutPart
import io.lukia.api.ProgressPart
import io.lukia.api.TokenResponse
import io.lukia.server.TestClock
import io.lukia.server.serverTest
import io.lukia.server.setUpAdmin
import io.lukia.server.signUp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class BookStateRoutesTest {

    @Test
    fun `a book's state is kept and listed as a change`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val change = BookStateRequest(progress = ProgressPart(41, 300, changedAt = 1_000))

        val stored: BookStateResponse = client.putState(admin, MobyDick, change).body()

        assertEquals(BookStateResponse(MobyDick, progress = ProgressPart(41, 300, 1_000)), stored)
        assertEquals(listOf(stored), client.stateChanges(admin).items)
    }

    @Test
    fun `the later change wins whichever arrives last`() = serverTest { client ->
        val admin = client.setUpAdmin()

        client.putState(admin, MobyDick, BookStateRequest(progress = ProgressPart(120, 300, changedAt = 2_000)))
        val stale: BookStateResponse =
            client.putState(
                admin,
                MobyDick,
                BookStateRequest(progress = ProgressPart(40, 300, changedAt = 1_000)),
            ).body()

        assertEquals(ProgressPart(120, 300, 2_000), stale.progress)
    }

    @Test
    fun `a change sent again is not a new change`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val change = BookStateRequest(favorite = FavoritePart(true, changedAt = 1_000))
        client.putState(admin, MobyDick, change)
        val seen = client.stateChanges(admin).cursor

        client.putState(admin, MobyDick, change)

        assertTrue(client.stateChanges(admin, since = seen).items.isEmpty())
    }

    @Test
    fun `each part wins or loses on its own`() = serverTest { client ->
        val admin = client.setUpAdmin()

        // The phone favourites the book; the desktop, later, only moves a page.
        client.putState(admin, MobyDick, BookStateRequest(favorite = FavoritePart(true, changedAt = 3_000)))
        client.putState(admin, MobyDick, BookStateRequest(progress = ProgressPart(9, 300, changedAt = 4_000)))
        // An old layout choice arrives late, alongside a still older un-favourite.
        val result: BookStateResponse = client.putState(
            admin,
            MobyDick,
            BookStateRequest(layout = LayoutPart("Text", 2_000), favorite = FavoritePart(false, changedAt = 1_000)),
        ).body()

        assertEquals(ProgressPart(9, 300, 4_000), result.progress)
        assertEquals(FavoritePart(true, 3_000), result.favorite)
        assertEquals(LayoutPart("Text", 2_000), result.layout)
    }

    @Test
    fun `a change dated in the future counts as now`() {
        val clock = TestClock()

        serverTest(clock = clock) { client ->
            val admin = client.setUpAdmin()
            val now = clock.millis()

            // A device whose clock runs a year fast...
            client.putState(
                admin,
                MobyDick,
                BookStateRequest(progress = ProgressPart(5, 300, now + 365.days.inWholeMilliseconds)),
            )
            clock.advance(2.minutes)
            // ...does not outvote a correct one for the next year.
            val later: BookStateResponse = client.putState(
                admin,
                MobyDick,
                BookStateRequest(progress = ProgressPart(6, 300, now + 1.minutes.inWholeMilliseconds)),
            ).body()

            assertEquals(6, later.progress?.pageIndex)
        }
    }

    @Test
    fun `nonsense is refused`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val progress = BookStateRequest(progress = ProgressPart(1, 300, 1_000))

        assertEquals(HttpStatusCode.BadRequest, client.putState(admin, "not-a-hash", progress).status)
        assertEquals(HttpStatusCode.BadRequest, client.putState(admin, MobyDick, BookStateRequest()).status)
        assertEquals(
            HttpStatusCode.BadRequest,
            client.putState(admin, MobyDick, BookStateRequest(progress = ProgressPart(-1, 300, 1_000))).status,
        )
        assertEquals(
            HttpStatusCode.BadRequest,
            client.putState(admin, MobyDick, BookStateRequest(layout = LayoutPart(" ", 1_000))).status,
        )
    }

    @Test
    fun `one account never sees or changes another's state`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val alice = client.signUp(admin, "alice")
        val bob = client.signUp(admin, "bob")

        client.putState(alice, MobyDick, BookStateRequest(progress = ProgressPart(200, 300, 1_000)))
        client.putState(bob, MobyDick, BookStateRequest(progress = ProgressPart(3, 300, 2_000)))

        assertEquals(listOf(ProgressPart(200, 300, 1_000)), client.stateChanges(alice).items.map { it.progress })
        assertEquals(listOf(ProgressPart(3, 300, 2_000)), client.stateChanges(bob).items.map { it.progress })
    }

    @Test
    fun `a deleted account's state goes with it`() = serverTest { client ->
        val admin = client.setUpAdmin()
        val alice = client.signUp(admin, "alice")
        client.putState(alice, MobyDick, BookStateRequest(favorite = FavoritePart(true, 1_000)))

        client.delete(ApiRoutes.Me) { bearerAuth(alice.accessToken) }
        val newAlice = client.signUp(admin, "alice")

        assertTrue(client.stateChanges(newAlice).items.isEmpty())
        assertNull(client.stateChanges(newAlice).items.firstOrNull())
    }

    @Test
    fun `reading state needs a signed-in account`() = serverTest { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get(ApiRoutes.MyBooks).status)
    }

    private suspend fun HttpClient.putState(by: TokenResponse, bookId: String, change: BookStateRequest): HttpResponse =
        put(ApiRoutes.myBook(bookId)) {
            bearerAuth(by.accessToken)
            contentType(ContentType.Application.Json)
            setBody(change)
        }

    private suspend fun HttpClient.stateChanges(by: TokenResponse, since: Long = 0): ChangePage<BookStateResponse> =
        get("${ApiRoutes.MyBooks}?since=$since") { bearerAuth(by.accessToken) }.body()
}

/** The hash of a book that need not be on the server at all. */
internal const val MobyDick = "3de332f731c6b7fa39018a1f12d5e9989333ad0d1d3d4f7174333e4e8ec21768"
