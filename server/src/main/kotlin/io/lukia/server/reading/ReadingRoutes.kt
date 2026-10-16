package io.lukia.server.reading

import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.util.getOrFail
import io.lukia.api.ApiRoutes
import io.lukia.api.BookStateRequest
import io.lukia.api.SessionRequest
import io.lukia.server.accounts.TokenService
import io.lukia.server.accounts.account
import io.lukia.server.accounts.signedIn
import io.lukia.server.http.ChangePageSize
import io.lukia.server.http.blocking
import io.lukia.server.http.since

/** Everything under `/me/…`: the signed-in account's own reading, and no one else's. */
fun Route.readingRoutes(bookStates: BookStateService, sessions: SessionService, tokens: TokenService) {
    signedIn(tokens) {
        get(ApiRoutes.MyBooks) {
            val since = call.since
            call.respond(blocking { bookStates.changesSince(call.account.id, since, ChangePageSize) })
        }

        put(ApiRoutes.MyBook) {
            val bookId = call.parameters.getOrFail("id")
            val request = call.receive<BookStateRequest>()
            call.respond(blocking { bookStates.apply(call.account.id, bookId, request, from = call.account.familyId) })
        }

        get(ApiRoutes.MySessions) {
            val since = call.since
            call.respond(blocking { sessions.changesSince(call.account.id, since, ChangePageSize) })
        }

        put(ApiRoutes.MySession) {
            val id = call.parameters.getOrFail("id")
            val request = call.receive<SessionRequest>()
            call.respond(blocking { sessions.record(call.account.id, id, request, from = call.account.familyId) })
        }
    }
}
