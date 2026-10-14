package io.lukia.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.lukia.api.ApiError
import io.lukia.api.ApiErrorCodes
import io.lukia.server.db.ServerDatabase
import io.lukia.server.health.healthRoutes
import org.slf4j.event.Level

/**
 * The whole server as one Ktor module, taking what it stores into as a
 * parameter so tests can hand it an in-memory database.
 */
fun Application.lukiaServer(config: ServerConfig, database: ServerDatabase) {
    install(ContentNegotiation) { json() }

    // Method, path, status and time only. Bodies and headers carry passwords
    // and tokens, and a log is the last place either should be copied to.
    install(CallLogging) { level = Level.INFO }

    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ApiError(ApiErrorCodes.NotFound, "No such endpoint."))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled failure", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(ApiErrorCodes.Internal, "The server failed to handle this request."),
            )
        }
    }

    routing {
        healthRoutes(config, database)
    }
}
