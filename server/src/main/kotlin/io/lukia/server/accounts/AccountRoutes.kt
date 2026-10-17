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
import io.lukia.api.ServerSettings
import io.lukia.server.http.blocking
import io.lukia.server.settings.ServerSettingsService

fun Route.accountRoutes(accounts: AccountService, tokens: TokenService, settings: ServerSettingsService) {
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

    post(ApiRoutes.SignUp) {
        val credentials = call.receive<Credentials>()
        val address = call.request.origin.remoteAddress
        call.respond(HttpStatusCode.Created, blocking { accounts.signUp(credentials, address) })
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

        adminRoutes(accounts, settings)
    }
}

/** Everything only the admin may do; each route checks, since the token alone says nothing about rank. */
private fun Route.adminRoutes(accounts: AccountService, settings: ServerSettingsService) {
    post(ApiRoutes.Invites) {
        call.requireAdmin()
        call.respond(HttpStatusCode.Created, blocking { accounts.createInvite(call.account) })
    }

    get(ApiRoutes.Settings) {
        call.requireAdmin()
        call.respond(blocking { settings.current() })
    }

    put(ApiRoutes.Settings) {
        call.requireAdmin()
        val changed = call.receive<ServerSettings>()
        call.respond(blocking { settings.update(changed) })
    }

    get(ApiRoutes.Accounts) {
        call.requireAdmin()
        call.respond(blocking { accounts.list() })
    }

    delete(ApiRoutes.Account) {
        call.requireAdmin()
        val username = call.parameters.getOrFail("username")
        blocking { accounts.remove(username) }
        call.respond(HttpStatusCode.NoContent)
    }

    put(ApiRoutes.AccountPassword) {
        call.requireAdmin()
        val username = call.parameters.getOrFail("username")
        val request = call.receive<NewPasswordRequest>()
        blocking { accounts.resetPassword(username, request.password) }
        call.respond(HttpStatusCode.NoContent)
    }
}
