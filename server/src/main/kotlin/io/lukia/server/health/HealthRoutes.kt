package io.lukia.server.health

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.lukia.api.ApiRoutes
import io.lukia.api.HealthResponse
import io.lukia.server.ServerConfig
import io.lukia.server.ServerVersion
import io.lukia.server.db.ServerDatabase
import io.lukia.server.settings.ServerSettingsService

/** Open to anyone: it is what an app asks before it knows whether to sign in or set up. */
fun Route.healthRoutes(config: ServerConfig, database: ServerDatabase, settings: ServerSettingsService) {
    get(ApiRoutes.Health) {
        call.respond(
            HealthResponse(
                name = config.name,
                version = ServerVersion,
                setupRequired = !database.accountQueries.hasAny().executeAsOne(),
                signUpOpen = settings.signUpOpen(),
            ),
        )
    }
}
