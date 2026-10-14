package io.lukia.server.admin

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.lukia.api.ApiRoutes
import io.lukia.server.serverTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AdminPageTest {

    @Test
    fun `the server's root is the admin page`() = serverTest { client ->
        val page = client.get("/")

        assertEquals(HttpStatusCode.OK, page.status)
        assertEquals(ContentType.Text.Html, page.contentType()?.withoutParameters())
        assertTrue("admin.js" in page.bodyAsText())
    }

    @Test
    fun `the page's script and style are served beside it`() = serverTest { client ->
        assertEquals(ContentType.Text.JavaScript, client.get("/admin.js").contentType()?.withoutParameters())
        assertEquals(ContentType.Text.CSS, client.get("/admin.css").contentType()?.withoutParameters())
    }

    @Test
    fun `every response forbids outside scripts, framing and type guessing`() = serverTest { client ->
        for (path in listOf("/", ApiRoutes.Health)) {
            val response = client.get(path)
            val policy = response.headers["Content-Security-Policy"].orEmpty()

            assertTrue("default-src 'self'" in policy, path)
            assertTrue("frame-ancestors 'none'" in policy, path)
            assertEquals("nosniff", response.headers["X-Content-Type-Options"], path)
        }
    }

    @Test
    fun `an unknown API path is still a JSON error, not the page`() = serverTest { client ->
        val response = client.get("${ApiRoutes.Prefix}/nothing")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
    }
}
