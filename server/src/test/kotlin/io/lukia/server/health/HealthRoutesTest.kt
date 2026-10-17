package io.lukia.server.health

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.lukia.api.ApiError
import io.lukia.api.ApiErrorCodes
import io.lukia.api.ApiRoutes
import io.lukia.api.HealthResponse
import io.lukia.server.ServerVersion
import io.lukia.server.inMemoryDatabase
import io.lukia.server.serverTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthRoutesTest {

    @Test
    fun `a new server says it needs setting up`() = serverTest { client ->
        val response = client.get(ApiRoutes.Health)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(
            HealthResponse("Test shelf", ServerVersion, setupRequired = true, signUpOpen = false),
            response.body(),
        )
    }

    @Test
    fun `a server with an account no longer needs setting up`() {
        val database = inMemoryDatabase()
        database.accountQueries.insert("admin", "hash", true, 0)

        serverTest(database) { client ->
            val health: HealthResponse = client.get(ApiRoutes.Health).body()

            assertEquals(false, health.setupRequired)
        }
    }

    @Test
    fun `an unknown endpoint answers in the error format`() = serverTest { client ->
        val response = client.get("${ApiRoutes.Prefix}/nothing-here")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ApiErrorCodes.NotFound, response.body<ApiError>().code)
    }
}
