package io.lukia.features.translate.infrastructure.openai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
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
import io.lukia.core.domain.FakeSecretStore
import io.lukia.core.domain.FakeSettingsStore
import io.lukia.core.domain.toAppError
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TranslationContext
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.TranslationSettings
import io.lukia.features.translate.domain.translate
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The provider that covers OpenAI and everything that imitates it - LM Studio,
 * Ollama, llama.cpp's server, vLLM.
 *
 * A mock engine rather than a stubbed client, so what is asserted is the bytes
 * on the request and the bytes on the response. The interesting cases are the
 * ones where a hosted service and somebody's laptop differ: one can be held to
 * a schema and the other cannot.
 */
class OpenAiTranslatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aTranslatedPassageIsReadOutOfTheAnswer() = runTest {
        val translator = translator(answers { ok("Kutsukaa minua Ismaeliksi.") })

        val translation = translator.translate(request())

        assertEquals("Kutsukaa minua Ismaeliksi.", translation.text)
        assertNull(translation.note, "an empty note is not a note")
    }

    /**
     * Streaming is the whole reason a slow model no longer looks like a broken
     * one, so the request has to actually ask for it.
     */
    @Test
    fun theAnswerIsAskedForAsAStream() = runTest {
        var body: JsonObject? = null
        val translator = translator(answers { body = it.jsonBody(); ok() })

        translator.translate(request())

        assertEquals(true, body?.get("stream")?.jsonPrimitive?.content?.toBoolean())
    }

    /**
     * What the reader watches: the sentence filling in, never a JSON fragment
     * and never anything the finished translation will contradict.
     */
    @Test
    fun theSentenceIsReportedAsItArrives() = runTest {
        val translator = translator(answers { ok("Kutsukaa minua Ismaeliksi.") })
        val seen = mutableListOf<String>()

        val translation = translator.translate(request()) { seen += it }

        assertTrue(seen.isNotEmpty(), "nothing was reported while the answer arrived")
        assertEquals("Kutsukaa minua Ismaeliksi.", seen.last())
        assertTrue(
            seen.all { translation.text.startsWith(it) },
            "something was shown that the finished translation contradicts: $seen"
        )
    }

    /** Nothing is worth showing until the model has begun the sentence itself. */
    @Test
    fun aModelStillIntroducingItselfReportsNothing() = runTest {
        val translator = translator(
            answers { content("Sure! Here you go:") },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234/v1",
            apiKey = null
        )
        val seen = mutableListOf<String>()

        runCatching { translator.translate(request()) { seen += it } }

        assertTrue(seen.isEmpty(), "prose was shown in place of the sentence: $seen")
    }

    /**
     * The failure this whole change is about. A model still loading is silent
     * for minutes, and telling somebody their server is not running while it is
     * busy loading is how they end up restarting the thing that was working.
     */
    @Test
    fun aModelTakingTooLongIsNotReportedAsAServerThatIsNotThere() = runTest {
        val translator = translator(
            MockEngine { request -> throw HttpRequestTimeoutException(request) },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234/v1",
            apiKey = null,
            model = "qwen2.5-7b-instruct"
        )

        val error = translator.failureFor(request()) as AppError.Unexpected
        val cause = error.cause.orEmpty()

        assertTrue(cause.contains("qwen2.5-7b-instruct"), cause)
        assertTrue(cause.contains("still loading"), cause)
        assertTrue(!cause.contains("server is running"), cause)
    }

    @Test
    fun theRequestGoesToTheChatEndpointUnderTheConfiguredAddress() = runTest {
        var sent: HttpRequestData? = null
        val translator = translator(answers { sent = it; ok() })

        translator.translate(request())

        assertEquals("https://api.openai.com/v1/chat/completions", sent?.url.toString())
    }

    /** A reader who types a trailing slash has not made a mistake. */
    @Test
    fun aTrailingSlashOnTheAddressIsNotADoubledOne() = runTest {
        var sent: HttpRequestData? = null
        val translator = translator(
            answers { sent = it; ok() },
            baseUrl = "https://api.openai.com/v1/"
        )

        translator.translate(request())

        assertEquals("https://api.openai.com/v1/chat/completions", sent?.url.toString())
    }

    @Test
    fun theKeyIsSentAsABearerToken() = runTest {
        var sent: HttpRequestData? = null
        val translator = translator(answers { sent = it; ok() }, apiKey = "sk-test")

        translator.translate(request())

        assertEquals("Bearer sk-test", sent?.headers?.get("Authorization"))
    }

    @Test
    fun thePromptTravelsAsASystemMessageAndAUserMessage() = runTest {
        var body: JsonObject? = null
        val translator = translator(answers { body = it.jsonBody(); ok() })

        translator.translate(
            request(context = TranslationContext(bookTitle = "Moby-Dick", genres = listOf("Whaling")))
        )

        val messages = body?.get("messages")?.jsonArray.orEmpty()
        val roles = messages.map { it.jsonObject["role"]?.jsonPrimitive?.content }
        val user = messages.last().jsonObject["content"]?.jsonPrimitive?.content.orEmpty()

        assertEquals(listOf("system", "user"), roles)
        assertTrue(user.contains("Moby-Dick"), user)
        assertTrue(user.contains("Whaling"), user)
    }

    /** A service that enforces a schema is given one, and the answer always parses. */
    @Test
    fun openAiIsHeldToASchema() = runTest {
        var body: JsonObject? = null
        val translator = translator(answers { body = it.jsonBody(); ok() })

        translator.translate(request())

        val format = body?.get("response_format")?.jsonObject
        assertEquals("json_schema", format?.get("type")?.jsonPrimitive?.content)

        val system = body?.get("messages")?.jsonArray?.first()
            ?.jsonObject?.get("content")?.jsonPrimitive?.content.orEmpty()
        assertTrue(!system.contains("JSON object"), "the schema was asked for twice")
    }

    /**
     * A model on somebody's laptop may simply ignore a schema it was handed, so
     * it is asked for the shape in words instead. Sending both would spend the
     * model's attention on formatting rules it is already bound by.
     */
    @Test
    fun aLocalServerIsAskedForTheShapeInWordsInstead() = runTest {
        var body: JsonObject? = null
        val translator = translator(
            answers { body = it.jsonBody(); ok() },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234/v1",
            apiKey = null
        )

        translator.translate(request())

        assertNull(body?.get("response_format"), "a local server was handed a schema")

        val system = body?.get("messages")?.jsonArray?.first()
            ?.jsonObject?.get("content")?.jsonPrimitive?.content.orEmpty()
        assertTrue(system.contains("JSON object"), system)
    }

    /** Most local servers want no key, and none is not a failure. */
    @Test
    fun aLocalServerIsAskedWithoutAKey() = runTest {
        var sent: HttpRequestData? = null
        val translator = translator(
            answers { sent = it; ok() },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234/v1",
            apiKey = null
        )

        translator.translate(request())

        assertNull(sent?.headers?.get("Authorization"))
    }

    /** One behind a proxy may want one, and giving one is not an error either. */
    @Test
    fun aLocalServerStillGetsAKeyWhenOneWasGiven() = runTest {
        var sent: HttpRequestData? = null
        val translator = translator(
            answers { sent = it; ok() },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234/v1",
            apiKey = "behind-a-proxy"
        )

        translator.translate(request())

        assertEquals("Bearer behind-a-proxy", sent?.headers?.get("Authorization"))
    }

    /**
     * A model that wrapped its answer in a code fence, or introduced it, has
     * still answered. Failing on that would break most local setups on their
     * first sentence.
     */
    @Test
    fun anAnswerWrappedInProseIsStillRead() = runTest {
        val translator = translator(
            answers {
                content(
                    "Sure! Here is the translation:\n" +
                        "```json\n{\"translation\": \"Hei.\", \"note\": \"\"}\n```\n" +
                        "Let me know if you need anything else."
                )
            },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234/v1",
            apiKey = null
        )

        assertEquals("Hei.", translator.translate(request()).text)
    }

    /** An answer with no object in it at all is a model that did not comply. */
    @Test
    fun proseWithNoAnswerInItIsAFailure() = runTest {
        val translator = translator(answers { content("I am unable to help with that.") })

        assertEquals(AppError.TranslationFailed, translator.failureFor(request()))
    }

    @Test
    fun withoutAKeyOpenAiIsNotAskedAtAll() = runTest {
        var asked = false
        val translator = translator(answers { asked = true; ok() }, apiKey = null)

        assertEquals(AppError.TranslationUnavailable, translator.failureFor(request()))
        assertTrue(!asked, "a request was sent without a key")
    }

    /**
     * The one thing this refuses outright: a passage out of somebody's book sent
     * across the internet in the clear.
     */
    @Test
    fun cleartextToThePublicInternetIsRefusedBeforeAnythingIsSent() = runTest {
        var asked = false
        val translator = translator(
            answers { asked = true; ok() },
            provider = TranslationProvider.Local,
            baseUrl = "http://models.example.com/v1",
            apiKey = null
        )

        val error = translator.failureFor(request()) as AppError.Unexpected

        assertTrue(error.cause.orEmpty().contains("plain HTTP"), error.cause.orEmpty())
        assertTrue(!asked, "a passage was sent in the clear")
    }

    /** The commonest state of a local server is not running. */
    @Test
    fun aServerThatIsNotThereSaysWhereItWasLookedFor() = runTest {
        val translator = translator(
            MockEngine { throw kotlinx.io.IOException("Connection refused") },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234/v1",
            apiKey = null
        )

        val error = translator.failureFor(request()) as AppError.Unexpected

        assertTrue(error.cause.orEmpty().contains("http://localhost:1234/v1"), error.cause.orEmpty())
    }

    @Test
    fun aRejectedKeySaysWhereToFixIt() = runTest {
        val translator = translator(answers { respondJson("{}", HttpStatusCode.Unauthorized) })

        val error = translator.failureFor(request()) as AppError.Unexpected
        assertTrue(error.cause.orEmpty().contains("Settings"), error.cause.orEmpty())
    }

    /** Pointing at the wrong path is the commonest mistake with a local server. */
    @Test
    fun nothingAnsweringAtTheAddressSaysSo() = runTest {
        val translator = translator(
            answers { respondJson("{}", HttpStatusCode.NotFound) },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234",
            apiKey = null
        )

        val error = translator.failureFor(request()) as AppError.Unexpected
        assertTrue(error.cause.orEmpty().contains("/v1"), error.cause.orEmpty())
    }

    /** A server that does not have the model says so better than this code could. */
    @Test
    fun theServersOwnExplanationIsPassedOn() = runTest {
        val translator = translator(
            answers {
                respondJson(
                    """{"error": {"message": "model 'qwen' not found", "type": "invalid_request_error"}}""",
                    HttpStatusCode.BadRequest
                )
            }
        )

        assertEquals(
            AppError.Unexpected("model 'qwen' not found"),
            translator.failureFor(request())
        )
    }

    /**
     * The failure a first-time setup actually hits. An account with no credit
     * and one asking too often arrive as the same status, and reporting both as
     * "try again shortly" sends somebody off to wait for a limit that was never
     * the reason.
     */
    @Test
    fun anAccountWithNoCreditIsNotReportedAsBeingTooBusy() = runTest {
        val translator = translator(
            answers {
                respondJson(
                    """{"error": {"message": "You exceeded your current quota.", "type": "insufficient_quota", "code": "insufficient_quota"}}""",
                    HttpStatusCode.TooManyRequests
                )
            }
        )

        val error = translator.failureFor(request()) as AppError.Unexpected

        assertTrue(error.cause.orEmpty().contains("out of API credit"), error.cause.orEmpty())
        assertTrue(!error.cause.orEmpty().contains("Try again in a moment"), error.cause.orEmpty())
    }

    /** A real rate limit still reads as one, in the service's own words. */
    @Test
    fun beingGenuinelyRateLimitedRepeatsWhatTheServiceSaid() = runTest {
        val translator = translator(
            answers {
                respondJson(
                    """{"error": {"message": "Rate limit reached for gpt-4.1", "type": "rate_limit_error"}}""",
                    HttpStatusCode.TooManyRequests
                )
            }
        )

        assertEquals(
            AppError.Unexpected("Rate limit reached for gpt-4.1"),
            translator.failureFor(request())
        )
    }

    /** With no explanation at all, the canned wording is still better than nothing. */
    @Test
    fun aBareRateLimitStillSaysSomethingUseful() = runTest {
        val translator = translator(answers { respondJson("{}", HttpStatusCode.TooManyRequests) })

        val error = translator.failureFor(request()) as AppError.Unexpected
        assertTrue(error.cause.orEmpty().contains("Try again"), error.cause.orEmpty())
    }

    @Test
    fun anAnswerCutShortSaysWhyRatherThanFailingToParse() = runTest {
        val translator = translator(
            answers { content("""{"translation": "Kut""", finishReason = "length") }
        )

        val error = translator.failureFor(request()) as AppError.Unexpected
        assertTrue(error.cause.orEmpty().contains("cut short"), error.cause.orEmpty())
    }

    private fun MockRequestHandleScope.ok(
        translation: String = "Hei.",
        note: String = ""
    ): HttpResponseData = content(
        json.encodeToString(
            buildJsonObject {
                put("translation", translation)
                put("note", note)
            }
        )
    )

    /**
     * An answer as a server really sends one: several chunks of text and a last
     * chunk carrying only the reason it stopped.
     *
     * Split into pieces on purpose - an answer arriving whole in one chunk would
     * pass without exercising any of the joining, which is the part of reading a
     * stream that can actually be wrong.
     */
    private fun MockRequestHandleScope.content(
        text: String,
        finishReason: String = "stop"
    ): HttpResponseData = respondStream(
        text.inThirds().map { contentChunk(it) } + finished(finishReason)
    )

    private fun contentChunk(text: String): String = json.encodeToString(
        buildJsonObject {
            put(
                "choices",
                buildJsonArray {
                    add(buildJsonObject { put("delta", buildJsonObject { put("content", text) }) })
                }
            )
        }
    )

    private fun finished(reason: String): String = json.encodeToString(
        buildJsonObject {
            put(
                "choices",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("delta", buildJsonObject { })
                            put("finish_reason", reason)
                        }
                    )
                }
            )
        }
    )

    private fun translator(
        engine: MockEngine,
        provider: TranslationProvider = TranslationProvider.OpenAi,
        apiKey: String? = "sk-test",
        baseUrl: String? = null,
        model: String = "gpt-4o"
    ): OpenAiTranslator {
        val settings = TranslationSettingsRepository(FakeSettingsStore(), FakeSecretStore()).apply {
            update(
                TranslationSettings(
                    targetLanguage = TargetLanguage.Finnish,
                    provider = provider
                )
                    .withModel(model)
                    .withApiKey(apiKey.orEmpty())
                    .let { if (baseUrl != null) it.withBaseUrl(baseUrl) else it }
            )
        }

        val client = HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json(json) }
            // As the app builds it: every request sets its own, from settings.
            install(HttpTimeout)
        }

        return OpenAiTranslator(client, settings, json)
    }

    private fun request(
        text: String = "Call me Ishmael.",
        context: TranslationContext = TranslationContext.None
    ) = TranslationRequest(
        text = text,
        targetLanguage = TargetLanguage.Finnish,
        context = context
    )

    private suspend fun OpenAiTranslator.failureFor(request: TranslationRequest): AppError =
        runCatching { translate(request) }.exceptionOrNull()?.toAppError()
            ?: error("expected a failure")

    private fun HttpRequestData.jsonBody(): JsonObject =
        json.parseToJsonElement((body as TextContent).text).jsonObject
}

private fun answers(
    handle: MockRequestHandleScope.(HttpRequestData) -> HttpResponseData
) = MockEngine { request -> handle(request) }

/**
 * A streamed answer, in the shape every OpenAI-compatible server sends one: a
 * `data:` line per chunk, a blank line between them, and `[DONE]` to close.
 */
private fun MockRequestHandleScope.respondStream(chunks: List<String>) = respond(
    content = chunks.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n",
    headers = headersOf("Content-Type", ContentType.Text.EventStream.toString())
)

/** Three pieces, so joining them is actually exercised. */
private fun String.inThirds(): List<String> = chunked((length + 2) / 3)

private fun MockRequestHandleScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK
) = respond(
    content = body,
    status = status,
    headers = headersOf("Content-Type", ContentType.Application.Json.toString())
)
