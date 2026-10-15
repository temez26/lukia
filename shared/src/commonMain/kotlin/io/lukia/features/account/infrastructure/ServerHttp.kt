package io.lukia.features.account.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import io.lukia.api.ApiError
import io.lukia.api.ApiErrorCodes
import io.lukia.core.domain.runCatchingApp
import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.domain.AccountProblem

/**
 * Requests to the sync server, and what each failure means to the app.
 *
 * Every call either answers or throws an [AccountFailure]: nothing answering is
 * [AccountProblem.ServerUnreachable], an answer in a shape the server would not
 * give is [AccountProblem.NotALukiaServer], and the server's own error codes
 * become the problems they name. Shared by every part of the app that talks to
 * the server, so a failure reads the same whichever asked.
 */
class ServerHttp(val client: HttpClient) {

    /** A request whose answer is a [T]. */
    suspend inline fun <reified T> call(crossinline request: suspend HttpClient.() -> HttpResponse): T {
        val response = send(request)
        return runCatchingApp { response.body<T>() }
            .getOrElse { throw AccountFailure(AccountProblem.NotALukiaServer) }
    }

    /** A request that succeeded, or the [AccountFailure] that says why not. */
    suspend inline fun send(crossinline request: suspend HttpClient.() -> HttpResponse): HttpResponse {
        val response = runCatchingApp { client.request() }
            .getOrElse { throw AccountFailure(AccountProblem.ServerUnreachable) }

        if (response.status.isSuccess()) return response
        throw AccountFailure(problemIn(response))
    }

    /** What a failed response says went wrong. One that does not say it the server's way is not the server. */
    suspend fun problemIn(response: HttpResponse): AccountProblem =
        runCatchingApp { response.body<ApiError>() }
            .map { problemFor(it.code) }
            .getOrDefault(AccountProblem.NotALukiaServer)

    private fun problemFor(code: String): AccountProblem = when (code) {
        ApiErrorCodes.Unauthorized -> AccountProblem.SessionExpired
        ApiErrorCodes.InvalidCredentials -> AccountProblem.SignInRejected
        ApiErrorCodes.TooManyAttempts -> AccountProblem.TooManyAttempts
        ApiErrorCodes.InvalidUsername -> AccountProblem.InvalidUsername
        ApiErrorCodes.InvalidPassword -> AccountProblem.InvalidPassword
        ApiErrorCodes.UsernameTaken -> AccountProblem.UsernameTaken
        ApiErrorCodes.InvalidInvite -> AccountProblem.InvalidInvite
        ApiErrorCodes.SetupDone -> AccountProblem.AlreadySetUp
        else -> AccountProblem.ServerFailed
    }
}
