package io.lukia.server.events

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.server.response.header
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.utils.io.writeStringUtf8
import io.lukia.api.ApiRoutes
import io.lukia.server.accounts.TokenService
import io.lukia.server.accounts.account
import io.lukia.server.accounts.signedIn
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * `GET /me/events`: a server-sent event stream that names each change the
 * signed-in account should catch up on - the shared library's, and its own -
 * as it happens. Held open for as long as the device listens.
 *
 * A comment line goes down it whenever it has been quiet for a while, so a
 * proxy in front, which closes connections that say nothing, keeps this one;
 * and `X-Accel-Buffering: no` asks nginx to pass each event on at once rather
 * than holding them back in a buffer.
 */
fun Route.eventRoutes(events: ChangeEvents, tokens: TokenService) {
    signedIn(tokens) {
        get(ApiRoutes.MyEvents) {
            val account = call.account
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.response.header("X-Accel-Buffering", "no")

            call.respondBytesWriter(contentType = ContentType.Text.EventStream) {
                // The first bytes go out at once, so the device knows it is listening.
                writeStringUtf8(": listening\n\n")
                flush()

                coroutineScope {
                    // Conflated: a device that is told twice before it reads catches up once.
                    val mine = Channel<Change>(Channel.CONFLATED)
                    launch {
                        events.all
                            .filter { it.isFor(account) }
                            .collect { mine.send(it) }
                    }
                    while (true) {
                        val change = withTimeoutOrNull(KeepAlive) { mine.receive() }
                        writeStringUtf8(change?.let { "event: ${it.kind.wire}\ndata: {}\n\n" } ?: ": still here\n\n")
                        flush()
                    }
                }
            }
        }
    }
}

/** Under the minute most proxies wait before they give up on a quiet connection. */
private val KeepAlive = 25.seconds
