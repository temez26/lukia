package io.lukia.features.translate.infrastructure.google

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.lukia.core.domain.AppError
import io.lukia.core.domain.storage.FakeSecretStore
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.core.domain.toAppError
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.domain.text.PassageUnit
import io.lukia.features.translate.domain.translate
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Google, which answers about a word and knows nothing about the book.
 *
 * The two endpoints are the interesting part. They ask completely differently
 * and answer in completely different shapes, and which is used comes down to
 * whether the reader has given a key - so what is worth proving is that the key
 * decides it, and that both shapes are read correctly.
 */
class GoogleTranslatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    /** The supported API, once there is a key to use it with. */
    @Test
    fun aKeyIsSentToTheSupportedApi() = runTest {
        var sent: HttpRequestData? = null
        val translator = translator(
            answers {
                sent = it
                respondJson(CLOUD_ANSWER)
            },
            apiKey = "google-key",
        )

        val translation = translator.translate(request())

        assertTrue(
            sent?.url.toString().startsWith("https://translation.googleapis.com/"),
            sent?.url.toString(),
        )
        assertTrue(sent?.url.toString().contains("key=google-key"), sent?.url.toString())
        assertEquals("valas", translation.text)
    }

    /** And the public one when there is not. */
    @Test
    fun withoutAKeyThePublicEndpointIsAsked() = runTest {
        var sent: HttpRequestData? = null
        val translator = translator(
            answers {
                sent = it
                respondJson(PUBLIC_ANSWER)
            },
            apiKey = null,
        )

        val translation = translator.translate(request())

        assertTrue(
            sent?.url.toString().startsWith("https://translate.googleapis.com/"),
            sent?.url.toString(),
        )
        assertEquals("valas", translation.text)
    }

    /** The language the reader is reading into, in the tag Google names it by. */
    @Test
    fun theTargetLanguageIsAskedFor() = runTest {
        var sent: HttpRequestData? = null
        val translator = translator(
            answers {
                sent = it
                respondJson(PUBLIC_ANSWER)
            },
            apiKey = null,
        )

        translator.translate(request())

        assertTrue(sent?.url.toString().contains("tl=fi"), sent?.url.toString())
    }

    /**
     * The public endpoint splits what it is given and answers piece by piece.
     * Joining them is the difference between a hyphenated word and half of one.
     */
    @Test
    fun everyPieceOfAPublicAnswerIsJoinedUp() = runTest {
        val translator = translator(
            answers { respondJson("""[[["itse","self",null,null,1],["-","-",null,null,1]]]""") },
            apiKey = null,
        )

        assertEquals("itse-", translator.translate(request("self-")).text)
    }

    /** Rate limiting is the ordinary state of the public endpoint. */
    @Test
    fun beingTurnedAwayWithoutAKeySaysToAddOne() = runTest {
        val translator = translator(
            answers { respondJson("", HttpStatusCode.TooManyRequests) },
            apiKey = null,
        )

        val error = translator.failure() as AppError.Unexpected

        assertTrue(error.explanation.orEmpty().contains("Cloud Translation key"), error.explanation.orEmpty())
    }

    /** A key the service will not take says so in its own words. */
    @Test
    fun theServicesOwnExplanationIsPassedOn() = runTest {
        val translator = translator(
            answers {
                respondJson(
                    """{"error": {"message": "API key not valid"}}""",
                    HttpStatusCode.BadRequest,
                )
            },
            apiKey = "wrong",
        )

        assertEquals(AppError.Unexpected("API key not valid"), translator.failure())
    }

    @Test
    fun anEmptyAnswerIsAFailureRatherThanAnEmptyWord() = runTest {
        val translator = translator(answers { respondJson("""[[]]""") }, apiKey = null)

        assertEquals(AppError.TranslationFailed, translator.failure())
    }

    private fun translator(engine: MockEngine, apiKey: String?): GoogleTranslator {
        val settings = TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore()).apply {
            update(
                TranslationSettings(targetLanguage = TargetLanguage.Finnish)
                    .withGoogleApiKey(apiKey.orEmpty()),
            )
        }

        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout)
        }

        return GoogleTranslator(client, settings, json)
    }

    private fun request(text: String = "whale") = TranslationRequest(
        text = text,
        targetLanguage = TargetLanguage.Finnish,
        unit = PassageUnit.Word,
    )

    private suspend fun GoogleTranslator.failure(): AppError =
        runCatching { translate(request()) }.exceptionOrNull()?.toAppError()
            ?: error("expected a failure")

    private companion object {

        const val CLOUD_ANSWER = """{"data": {"translations": [{"translatedText": "valas"}]}}"""

        /** Nested arrays, one entry per piece, each with the translation first. */
        const val PUBLIC_ANSWER = """[[["valas","whale",null,null,1]],null,"en"]"""
    }
}

private fun answers(
    handle: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) = MockEngine { request -> handle(request) }

private fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = body,
    status = status,
    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
)
