package io.lukia.features.translate.infrastructure.anthropic

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.lukia.core.domain.AppError
import io.lukia.core.domain.storage.FakeSecretStore
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.core.domain.toAppError
import io.lukia.features.translate.domain.TranslationContext
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.provider.TranslationProvider
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.domain.translate
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The translator under test, the answers the service sends it, and the requests
 * it is given.
 *
 * Shared by this provider's test classes so all of them hold the same client to
 * the same wire format.
 */

internal val json = Json { ignoreUnknownKeys = true }

internal fun MockEngineScope.ok(
    translation: String = "Hei.",
    note: String = "",
    stopReason: String = "end_turn",
): HttpResponseData {
    val passage = json.encodeToString(
        buildJsonObject {
            put("translation", translation)
            put("note", note)
        },
    )

    return respondStream(
        passage.inThirds().map { textDelta(it) } + stopped(stopReason) + messageStop(),
    )
}

/** The event that closes a finished message, as the service really sends it. */
internal fun messageStop(): String = json.encodeToString(
    buildJsonObject { put("type", "message_stop") },
)

/** One `content_block_delta` carrying part of the answer. */
internal fun textDelta(text: String): String = json.encodeToString(
    buildJsonObject {
        put("type", "content_block_delta")
        put(
            "delta",
            buildJsonObject {
                put("type", "text_delta")
                put("text", text)
            },
        )
    },
)

/** The `message_delta` that ends every answer, saying why the model stopped. */
internal fun stopped(reason: String): String = json.encodeToString(
    buildJsonObject {
        put("type", "message_delta")
        put("delta", buildJsonObject { put("stop_reason", reason) })
    },
)

internal fun translator(
    engine: MockEngine,
    apiKey: String? = "sk-ant-test",
    model: String = "claude-opus-5",
): AnthropicTranslator {
    val settings = TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore()).apply {
        update(
            TranslationSettings(
                targetLanguage = TargetLanguage.Finnish,
                provider = TranslationProvider.Anthropic,
            ).withModel(model).withApiKey(apiKey.orEmpty()),
        )
    }

    val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        // As the app builds it: every request sets its own, from settings.
        install(HttpTimeout)
    }

    return AnthropicTranslator(client, settings, json = json)
}

internal fun request(
    text: String = "Call me Ishmael.",
    context: TranslationContext = TranslationContext.None,
) = TranslationRequest(
    text = text,
    targetLanguage = TargetLanguage.Finnish,
    context = context,
)

internal suspend fun AnthropicTranslator.failureFor(request: TranslationRequest): AppError =
    runCatching { translate(request) }.exceptionOrNull()?.toAppError()
        ?: error("expected a failure")

internal fun HttpRequestData.jsonBody(): JsonObject =
    json.parseToJsonElement((body as TextContent).text).jsonObject

internal typealias MockEngineScope = io.ktor.client.engine.mock.MockRequestHandleScope

internal fun answers(
    handle: MockEngineScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
) = MockEngine { request -> handle(request) }

/**
 * A streamed answer, in the shape this service sends one: a `data:` line per
 * event and a blank line between them. No `[DONE]` - this one closes with a
 * `message_stop` event instead, which is what [messageStop] is for.
 */
internal fun MockEngineScope.respondStream(events: List<String>) = respond(
    content = events.joinToString("") { "data: $it\n\n" },
    headers = headersOf("Content-Type", ContentType.Text.EventStream.toString()),
)

/** Three pieces, so joining them is actually exercised. */
internal fun String.inThirds(): List<String> = chunked((length + 2) / 3)

internal fun MockEngineScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = body.trimIndent(),
    status = status,
    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
)
