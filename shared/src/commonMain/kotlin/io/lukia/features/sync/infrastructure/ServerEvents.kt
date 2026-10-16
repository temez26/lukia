package io.lukia.features.sync.infrastructure

import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import io.lukia.api.ApiRoutes
import io.lukia.features.account.domain.AccountFailure
import io.lukia.features.account.infrastructure.ServerHttp

/**
 * The server's stream of changes - see `ApiRoutes.MyEvents`. Each one is only a
 * hint to catch up now; what changed is still asked for the usual way.
 */
class ServerEvents(private val http: ServerHttp) {

    /**
     * Listens until the connection ends, calling [onChange] with the kind of
     * each change as it is announced. Ends quietly when the server closes the
     * stream, and throws when it cannot be reached or refuses the token.
     *
     * No limit on the whole request, only on silence: the server says
     * something at least twice a minute, so a minute without a word is a
     * connection that has died without saying so.
     */
    suspend fun listen(address: String, token: String, onChange: suspend (kind: String) -> Unit) {
        http.client.prepareGet(address + ApiRoutes.MyEvents) {
            bearerAuth(token)
            timeout {
                requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
                socketTimeoutMillis = SilenceMillis
            }
        }.execute { response ->
            if (!response.status.isSuccess()) throw AccountFailure(http.problemIn(response))

            val stream = response.bodyAsChannel()
            while (true) {
                val line = stream.readUTF8Line() ?: break
                if (line.startsWith(EventField)) onChange(line.removePrefix(EventField).trim())
            }
        }
    }

    private companion object {
        const val EventField = "event:"
        const val SilenceMillis = 60_000L
    }
}
