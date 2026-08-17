package io.lukia.features.translate.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * How long a reader will wait with a finger on the page before the answer is
 * worth less than the waiting. Past this they would rather be told it failed
 * and tap again.
 */
private const val REQUEST_TIMEOUT_MILLIS = 30_000L

/**
 * The client every translation goes through.
 *
 * No engine is named: each platform puts exactly one on its own classpath -
 * OkHttp on Android and the desktop, Darwin on iOS - and Ktor resolves it from
 * there. That is what keeps this one function rather than an expect/actual pair.
 *
 * `ignoreUnknownKeys` because the wire format is somebody else's and gains
 * fields: a response carrying something new should translate the sentence, not
 * fail to parse.
 */
fun translationHttpClient(): HttpClient = HttpClient {
    expectSuccess = false

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    install(HttpTimeout) {
        requestTimeoutMillis = REQUEST_TIMEOUT_MILLIS
    }
}
