package io.lukia.server.http

import io.ktor.server.application.createApplicationPlugin

/**
 * Headers for a server that serves no pages: nothing it answers may load
 * anything, be framed by another site, or have its content type guessed at -
 * so a response a browser were somehow tricked into rendering does nothing.
 */
val SecurityHeaders = createApplicationPlugin("SecurityHeaders") {
    onCall { call ->
        call.response.headers.append(
            "Content-Security-Policy",
            "default-src 'none'; frame-ancestors 'none'",
        )
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.response.headers.append("Referrer-Policy", "no-referrer")
    }
}
