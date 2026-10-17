package io.lukia.server.http

import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.lukia.api.ApiRoutes
import io.lukia.server.serverTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SecurityHeadersTest {

    @Test
    fun `every response forbids loading anything, framing and type guessing`() = serverTest { client ->
        for (path in listOf("/", ApiRoutes.Health)) {
            val response = client.get(path)
            val policy = response.headers["Content-Security-Policy"].orEmpty()

            assertTrue("default-src 'none'" in policy, path)
            assertTrue("frame-ancestors 'none'" in policy, path)
            assertEquals("nosniff", response.headers["X-Content-Type-Options"], path)
        }
    }

    /** The app is how the server is run now; the old admin page's address is just another unknown path. */
    @Test
    fun `the server serves no page at its root`() = serverTest { client ->
        for (path in listOf("/", "/admin.js", "${ApiRoutes.Prefix}/nothing")) {
            val response = client.get(path)

            assertEquals(HttpStatusCode.NotFound, response.status, path)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters(), path)
        }
    }
}
