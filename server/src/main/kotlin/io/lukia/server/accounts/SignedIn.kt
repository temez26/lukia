package io.lukia.server.accounts

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.request.header
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import io.ktor.util.AttributeKey
import io.lukia.api.ApiErrorCodes
import io.lukia.server.http.ApiException
import io.lukia.server.http.blocking

/**
 * Routes declared inside need a valid access token; the account it belongs to
 * is then [ApplicationCall.account].
 *
 * Every query that touches one account's data takes its id from here and never
 * from the request, which is what keeps one account out of another's state.
 */
fun Route.signedIn(tokens: TokenService, build: Route.() -> Unit): Route {
    val route = createChild(SignedInSelector)
    route.install(RequireSignIn) { this.tokens = tokens }
    route.build()
    return route
}

val ApplicationCall.account: SignedInAccount get() = attributes[SignedInKey]

fun ApplicationCall.requireAdmin() {
    if (!account.isAdmin) throw ApiException(HttpStatusCode.Forbidden, ApiErrorCodes.Forbidden, "Admin only.")
}

class RequireSignInConfig {
    lateinit var tokens: TokenService
}

private val RequireSignIn = createRouteScopedPlugin("RequireSignIn", ::RequireSignInConfig) {
    val tokens = pluginConfig.tokens

    onCall { call ->
        val token = call.request.header("Authorization")
            ?.takeIf { it.startsWith(BearerPrefix, ignoreCase = true) }
            ?.substring(BearerPrefix.length)
            ?.trim()

        val account = token?.let { blocking { tokens.accountFor(it) } }
            ?: throw ApiException(HttpStatusCode.Unauthorized, ApiErrorCodes.Unauthorized, "Sign in first.")

        call.attributes.put(SignedInKey, account)
    }
}

/** Adds nothing to the path; it exists so the plugin above has a route of its own to sit on. */
private object SignedInSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) =
        RouteSelectorEvaluation.Transparent

    override fun toString() = "(signed in)"
}

private val SignedInKey = AttributeKey<SignedInAccount>("SignedInAccount")

private const val BearerPrefix = "Bearer "
