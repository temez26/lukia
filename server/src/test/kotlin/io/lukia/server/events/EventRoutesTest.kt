package io.lukia.server.events

import io.ktor.client.request.prepareGet
import io.ktor.http.HttpStatusCode
import io.lukia.api.ApiRoutes
import io.lukia.server.serverTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The stream itself is exercised end to end against a real server in the app's
 * tests (`LiveChangesTest`): the test host here does not hand a response that
 * never ends to its client.
 */
class EventRoutesTest {

    @Test
    fun `listening needs a sign-in`() = serverTest { client ->
        client.prepareGet(ApiRoutes.MyEvents).execute { response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }
    }
}
