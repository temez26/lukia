package io.lukia.features.translate.infrastructure

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.HttpTimeoutConfig
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.serialization.kotlinx.json.json
import io.lukia.features.translate.domain.settings.TranslationSettings
import kotlinx.serialization.json.Json

/**
 * How long a server has to answer the door.
 *
 * Opening a connection is the one part of this that takes the same time
 * everywhere: a machine that is listening accepts in milliseconds whether it is
 * across the room or across the internet, and one that is not listening refuses
 * immediately. So this stays short and fixed while everything after it is
 * configurable - which is what lets a wrong address fail in seconds even when
 * the reader has allowed the model five minutes to think.
 */
private const val CONNECT_TIMEOUT_MILLIS = 10_000L

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

    // A redirect is somebody else choosing where the request goes, and the
    // check that says a plain-HTTP address is the reader's own machine has
    // already been made against the address they configured - see
    // `isAllowedTranslationEndpoint`. Following one would move the request to a
    // host nothing has checked, so the answer to a 3xx is the same as the
    // answer to any other unexpected status: report it and send nothing more.
    // No provider this app speaks to redirects its API.
    followRedirects = false

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }

    // Only the part that is the same for every request. What a given request is
    // allowed to take is decided per request, by translationTimeouts below.
    install(HttpTimeout) {
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
    }
}

/**
 * How long this particular request may take, from what the reader has allowed
 * the provider they are using.
 *
 * The total is deliberately unbounded and the silence is not. A single deadline
 * over the whole request cannot tell a model writing a long answer from a server
 * that has stopped talking, so it must be long enough for the slowest legitimate
 * answer - by which point it catches nothing. Bounding the gaps gives up the
 * moment a request stops making progress, and never on one still arriving.
 *
 * This is why a translation through a local model no longer fails at thirty
 * seconds: loading a model is one long silence, and the reader says how long a
 * silence is too long.
 */
internal fun HttpRequestBuilder.translationTimeouts(settings: TranslationSettings) {
    timeout {
        connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS
        socketTimeoutMillis = settings.timeoutSeconds * 1000L
        requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
    }
}
