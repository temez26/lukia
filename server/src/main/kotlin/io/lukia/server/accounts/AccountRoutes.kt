package io.lukia.server.accounts

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.util.getOrFail
import io.lukia.api.AccountResponse
import io.lukia.api.ApiRoutes
import io.lukia.api.Credentials
import io.lukia.api.NewPasswordRequest
import io.lukia.api.RedeemInviteRequest
import io.lukia.api.RefreshRequest
import io.lukia.server.http.blocking

fun Route.accountRoutes(accounts: AccountService, tokens: TokenService) {
    post(ApiRoutes.Setup) {
        val credentials = call.receive<Credentials>()
        call.respond(HttpStatusCode.Created, blocking { accounts.setup(credentials) })
    }

    post(ApiRoutes.SignIn) {
        val credentials = call.receive<Credentials>()
        val address = call.request.origin.remoteAddress
        call.respond(blocking { accounts.signIn(credentials, address) })
    }

    post(ApiRoutes.Refresh) {
        val request = call.receive<RefreshRequest>()
        call.respond(blocking { tokens.refresh(request.refreshToken) })
    }

    post(ApiRoutes.Redeem) {
        val request = call.receive<RedeemInviteRequest>()
        call.respond(HttpStatusCode.Created, blocking { accounts.redeem(request) })
    }

    signedIn(tokens) {
        post(ApiRoutes.SignOut) {
            blocking { tokens.revoke(call.account.familyId) }
            call.respond(HttpStatusCode.NoContent)
        }

        get(ApiRoutes.Me) {
            call.respond(AccountResponse(call.account.username, call.account.isAdmin))
        }

        delete(ApiRoutes.Me) {
            blocking { accounts.delete(call.account) }
            call.respond(HttpStatusCode.NoContent)
        }

        post(ApiRoutes.Invites) {
            call.requireAdmin()
            call.respond(HttpStatusCode.Created, blocking { accounts.createInvite(call.account) })
        }

        put(ApiRoutes.AccountPassword) {
            call.requireAdmin()
            val username = call.parameters.getOrFail("username")
            val request = call.receive<NewPasswordRequest>()
            blocking { accounts.resetPassword(username, request.password) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
