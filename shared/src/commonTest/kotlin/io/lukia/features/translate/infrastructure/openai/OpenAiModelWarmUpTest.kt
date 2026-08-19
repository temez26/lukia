package io.lukia.features.translate.infrastructure.openai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.lukia.core.domain.AppError
import io.lukia.core.domain.FakeSecretStore
import io.lukia.core.domain.FakeSettingsStore
import io.lukia.core.domain.toAppError
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.TranslationSettings
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Having a local model loaded before anybody is waiting on it.
 *
 * There is no "please load this" in the API every local server implements, so
 * what is asserted here is that the smallest possible piece of real work is
 * sent - and that when it fails, it fails in words about the model rather than
 * about the network.
 */
class OpenAiModelWarmUpTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun theChosenModelIsAskedToRunSomething() = runTest {
        var body: JsonObject? = null
        var sent: HttpRequestData? = null
        val warmUp = warmUp(
            answers {
                sent = it
                body = it.jsonBody()
                ok()
            },
            model = "qwen2.5-7b-instruct",
        )

        warmUp.warmUp()

        assertEquals("http://localhost:1234/v1/chat/completions", sent?.url.toString())
        assertEquals("qwen2.5-7b-instruct", body?.get("model")?.jsonPrimitive?.content)
    }

    /** The answer is thrown away, so asking for more of it would be waste. */
    @Test
    fun onlyASingleTokenIsAskedFor() = runTest {
        var body: JsonObject? = null
        val warmUp = warmUp(
            answers {
                body = it.jsonBody()
                ok()
            },
        )

        warmUp.warmUp()

        assertEquals(1, body?.get("max_tokens")?.jsonPrimitive?.content?.toInt())
    }

    /** A name that does not match anything loaded is the usual way this fails. */
    @Test
    fun aModelTheServerDoesNotHaveIsReportedInItsOwnWords() = runTest {
        val warmUp = warmUp(
            answers {
                respondJson(
                    """{"error": {"message": "model 'qwen' not found"}}""",
                    HttpStatusCode.NotFound,
                )
            },
        )

        assertEquals(AppError.Unexpected("model 'qwen' not found"), warmUp.failure())
    }

    /**
     * The wait this exists to move: loading is exactly what takes minutes, so
     * running out of patience here has to read as a slow load rather than as a
     * server that is not running.
     */
    @Test
    fun aLoadThatRanOutOfTimeSaysWhatItWasWaitingFor() = runTest {
        val warmUp = warmUp(MockEngine { throw HttpRequestTimeoutException(it) })

        val error = warmUp.failure() as AppError.Unexpected

        assertTrue(error.cause.orEmpty().contains("still loading"), error.cause.orEmpty())
    }

    private fun warmUp(
        engine: MockEngine,
        model: String = "local-model",
    ): OpenAiModelWarmUp {
        val settings = TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore()).apply {
            update(
                TranslationSettings(
                    targetLanguage = TargetLanguage.Finnish,
                    provider = TranslationProvider.Local,
                ).withModel(model),
            )
        }

        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout)
        }

        return OpenAiModelWarmUp(client, settings, json)
    }

    private suspend fun OpenAiModelWarmUp.failure(): AppError =
        runCatching { warmUp() }.exceptionOrNull()?.toAppError() ?: error("expected a failure")

    private fun HttpRequestData.jsonBody(): JsonObject =
        json.parseToJsonElement((body as TextContent).text).jsonObject
}

private fun answers(
    handle: MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
) = MockEngine { request -> handle(request) }

private fun MockRequestHandleScope.ok() = respondJson("""{"choices": []}""")

private fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = body,
    status = status,
    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
)
