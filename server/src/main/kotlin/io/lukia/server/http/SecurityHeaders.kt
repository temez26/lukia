package io.lukia.server.http

import io.ktor.server.application.createApplicationPlugin

/**
 * Headers that keep the admin page to itself: no script, style or image from
 * anywhere but this server (covers arrive as `blob:` URLs, fetched with the
 * token), no framing by another site, and no guessing at content types.
 *
 * On every response rather than only the page's, because they cost a few bytes
 * and a JSON response that a browser were somehow tricked into rendering is
 * then held to the same rules.
 */
val SecurityHeaders = createApplicationPlugin("SecurityHeaders") {
    onCall { call ->
        call.response.headers.append(
            "Content-Security-Policy",
            "default-src 'self'; img-src 'self' blob:; object-src 'none'; base-uri 'none'; frame-ancestors 'none'",
        )
        call.response.headers.append("X-Content-Type-Options", "nosniff")
        call.response.headers.append("Referrer-Policy", "no-referrer")
    }
}
