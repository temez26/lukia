package io.lukia.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.http.content.staticResources
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.lukia.api.ApiError
import io.lukia.api.ApiErrorCodes
import io.lukia.server.accounts.AccountService
import io.lukia.server.accounts.Argon2PasswordHasher
import io.lukia.server.accounts.PasswordHasher
import io.lukia.server.accounts.SecretTokens
import io.lukia.server.accounts.SignInThrottle
import io.lukia.server.accounts.TokenService
import io.lukia.server.accounts.accountRoutes
import io.lukia.server.db.ServerDatabase
import io.lukia.server.events.ChangeEvents
import io.lukia.server.events.eventRoutes
import io.lukia.server.health.healthRoutes
import io.lukia.server.http.ApiException
import io.lukia.server.http.JsonBodyLimit
import io.lukia.server.http.SecurityHeaders
import io.lukia.server.library.BookFiles
import io.lukia.server.library.LibraryService
import io.lukia.server.library.libraryRoutes
import io.lukia.server.reading.BookStateService
import io.lukia.server.reading.SessionService
import io.lukia.server.reading.readingRoutes
import org.slf4j.event.Level
import java.time.Clock

/**
 * The whole server as one Ktor module, taking what it stores into, its clock
 * and its password hash as parameters so tests can hand it an in-memory
 * database, move time and skip Argon2's deliberate slowness.
 */
fun Application.lukiaServer(
    config: ServerConfig,
    database: ServerDatabase,
    clock: Clock = Clock.systemUTC(),
    hasher: PasswordHasher = Argon2PasswordHasher(),
) {
    val secrets = SecretTokens()
    val tokens = TokenService(database, secrets, clock)
    val accounts = AccountService(database, hasher, tokens, secrets, SignInThrottle(clock), clock)
    val files = BookFiles(config.dataDirectory)
    val events = ChangeEvents()
    val library = LibraryService(database, files, clock, events)
    val bookStates = BookStateService(database, clock, events)
    val sessions = SessionService(database, events)

    install(ContentNegotiation) { json() }
    install(SecurityHeaders)
    install(JsonBodyLimit)

    // Lets a download that broke off resume where it stopped, rather than
    // starting a hundred-megabyte PDF again.
    install(PartialContent)

    // Method, path, status and time only. Bodies and headers carry passwords
    // and tokens, and a log is the last place either should be copied to.
    install(CallLogging) { level = Level.INFO }

    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ApiError(cause.code, cause.message))
        }
        // A body that is not the JSON the endpoint expects.
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ApiError(ApiErrorCodes.BadRequest, "The request is malformed."))
        }
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
        accountRoutes(accounts, tokens)
        libraryRoutes(library, files, tokens, config.maxUploadBytes)
        readingRoutes(bookStates, sessions, tokens)
        eventRoutes(events, tokens)

        // The admin page: the only HTML the server serves. See resources/admin.
        staticResources("/", "admin")
    }
}
