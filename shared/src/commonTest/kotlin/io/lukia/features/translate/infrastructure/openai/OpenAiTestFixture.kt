package io.lukia.features.translate.infrastructure.openai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
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
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * The translator under test, the answers a server sends it, and the requests it
 * is given.
 *
 * Shared by this provider's test classes so that all of them are holding the
 * same translator to the same wire format.
 */

internal val json = Json { ignoreUnknownKeys = true }

internal fun MockRequestHandleScope.ok(
    translation: String = "Hei.",
    note: String = "",
): HttpResponseData = content(
    json.encodeToString(
        buildJsonObject {
            put("translation", translation)
            put("note", note)
        },
    ),
)

/**
 * An answer as a server really sends one: several chunks of text and a last
 * chunk carrying only the reason it stopped.
 *
 * Split into pieces on purpose - an answer arriving whole in one chunk would
 * pass without exercising any of the joining, which is the part of reading a
 * stream that can actually be wrong.
 */
internal fun MockRequestHandleScope.content(
    text: String,
    finishReason: String = "stop",
): HttpResponseData = respondStream(
    text.inThirds().map { contentChunk(it) } + finished(finishReason),
)

internal fun contentChunk(text: String): String = json.encodeToString(
    buildJsonObject {
        put(
            "choices",
            buildJsonArray {
                add(buildJsonObject { put("delta", buildJsonObject { put("content", text) }) })
            },
        )
    },
)

internal fun finished(reason: String): String = json.encodeToString(
    buildJsonObject {
        put(
            "choices",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("delta", buildJsonObject { })
                        put("finish_reason", reason)
                    },
                )
            },
        )
    },
)

internal fun translator(
    engine: MockEngine,
    provider: TranslationProvider = TranslationProvider.OpenAi,
    apiKey: String? = "sk-test",
    baseUrl: String? = null,
    model: String = "gpt-4o",
): OpenAiTranslator {
    val settings = TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore()).apply {
        update(
            TranslationSettings(
                targetLanguage = TargetLanguage.Finnish,
                provider = provider,
            )
                .withModel(model)
                .withApiKey(apiKey.orEmpty())
                .let { if (baseUrl != null) it.withBaseUrl(baseUrl) else it },
        )
    }

    val client = HttpClient(engine) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        // As the app builds it: every request sets its own, from settings.
        install(HttpTimeout)
    }

    return OpenAiTranslator(client, settings, json = json)
}

internal fun request(
    text: String = "Call me Ishmael.",
    context: TranslationContext = TranslationContext.None,
) = TranslationRequest(
    text = text,
    targetLanguage = TargetLanguage.Finnish,
    context = context,
)

internal suspend fun OpenAiTranslator.failureFor(request: TranslationRequest): AppError =
    runCatching { translate(request) }.exceptionOrNull()?.toAppError()
        ?: error("expected a failure")

internal fun HttpRequestData.jsonBody(): JsonObject =
    json.parseToJsonElement((body as TextContent).text).jsonObject

internal fun answers(
    handle: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) = MockEngine { request -> handle(request) }

/**
 * A streamed answer, in the shape every OpenAI-compatible server sends one: a
 * `data:` line per chunk, a blank line between them, and `[DONE]` to close.
 */
internal fun MockRequestHandleScope.respondStream(chunks: List<String>) = respond(
    content = chunks.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n",
    headers = headersOf("Content-Type", ContentType.Text.EventStream.toString()),
)

/** Three pieces, so joining them is actually exercised. */
internal fun String.inThirds(): List<String> = chunked((length + 2) / 3)

internal fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    // Trimmed so a test can write its response as an indented raw string.
    content = body.trimIndent(),
    status = status,
    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
)
