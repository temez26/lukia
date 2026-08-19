package io.lukia.features.translate.infrastructure.anthropic

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
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
 * The provider against the wire format it actually speaks.
 *
 * A mock engine rather than a stubbed client, so what is asserted is the bytes
 * on the request and the bytes on the response - the two things a hand-written
 * API client can get wrong and a type system cannot catch.
 */
class AnthropicTranslatorTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun aTranslatedPassageIsReadOutOfTheAnswer() = runTest {
        val translator = translator(answers { ok(translation = "Kutsukaa minua Ismaeliksi.") })

        val translation = translator.translate(request())

        assertEquals("Kutsukaa minua Ismaeliksi.", translation.text)
        assertNull(translation.note, "an empty note is not a note")
    }

    @Test
    fun aNoteWorthMakingIsKept() = runTest {
        val translator = translator(
            answers { ok(translation = "Hän veti aseensa.", note = "\"piece\" is a weapon here.") },
        )

        assertEquals("\"piece\" is a weapon here.", translator.translate(request()).note)
    }

    /**
     * Thinking is on by default on the models this offers, and its block comes
     * first. Reading `content[0]` would get an empty string on exactly the
     * models the app defaults to.
     */
    @Test
    fun theAnswerIsFoundPastAnyThinkingTheModelDid() = runTest {
        val thinking = json.encodeToString(
            buildJsonObject {
                put("type", "content_block_delta")
                put(
                    "delta",
                    buildJsonObject {
                        put("type", "thinking_delta")
                        put("thinking", "The greeting is informal here.")
                    },
                )
            },
        )

        val translator = translator(
            answers {
                respondStream(
                    listOf(thinking, textDelta("""{"translation":"Hei.","note":""}""")) +
                        stopped("end_turn"),
                )
            },
        )

        assertEquals("Hei.", translator.translate(request()).text)
    }

    @Test
    fun theAnswerIsAskedForAsAStream() = runTest {
        var body: JsonObject? = null
        val translator = translator(
            answers {
                body = it.jsonBody()
                ok()
            },
        )

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
            "something was shown that the finished translation contradicts: $seen",
        )
    }

    /** A wait that ran out is not a service that could not be reached. */
    @Test
    fun aServiceTakingTooLongSaysSoRatherThanBlamingTheConnection() = runTest {
        val translator = translator(MockEngine { throw HttpRequestTimeoutException(it) })

        val error = translator.failureFor(request()) as AppError.Unexpected
        val cause = error.cause.orEmpty()

        assertTrue(cause.contains("did not answer"), cause)
        assertTrue(cause.contains("Settings"), cause)
    }

    @Test
    fun theKeyAndTheApiVersionAreSentOnEveryRequest() = runTest {
        var sent: HttpRequestData? = null
        val translator = translator(
            answers {
                sent = it
                ok()
            },
            apiKey = "sk-ant-test",
        )

        translator.translate(request())

        assertEquals("sk-ant-test", sent?.headers?.get("x-api-key"))
        assertEquals("2023-06-01", sent?.headers?.get("anthropic-version"))
        assertEquals("https://api.anthropic.com/v1/messages", sent?.url.toString())
    }

    @Test
    fun theChosenModelIsWhatIsAsked() = runTest {
        var body: JsonObject? = null
        val translator = translator(
            answers {
                body = it.jsonBody()
                ok()
            },
            model = "claude-sonnet-5",
        )

        translator.translate(request())

        assertEquals("claude-sonnet-5", body?.get("model")?.jsonPrimitive?.content)
    }

    /**
     * The model is free text now, so a name this build has never heard of is
     * passed through rather than refused - the service knows its own catalogue
     * better than a list compiled when the app shipped.
     */
    @Test
    fun aModelThisBuildHasNeverHeardOfIsStillAsked() = runTest {
        var body: JsonObject? = null
        val translator = translator(
            answers {
                body = it.jsonBody()
                ok()
            },
            model = "claude-something-not-yet-released",
        )

        translator.translate(request())

        assertEquals(
            "claude-something-not-yet-released",
            body?.get("model")?.jsonPrimitive?.content,
        )
    }

    /** The book reaches the model, which is the whole point of the context. */
    @Test
    fun theBookTravelsWithTheSentence() = runTest {
        var body: JsonObject? = null
        val translator = translator(
            answers {
                body = it.jsonBody()
                ok()
            },
        )

        translator.translate(
            request(
                context = TranslationContext(
                    bookTitle = "Moby-Dick",
                    genres = listOf("Whaling"),
                ),
            ),
        )

        val message = body?.get("messages")?.jsonArray?.first()?.jsonObject
        val content = message?.get("content")?.jsonPrimitive?.content.orEmpty()

        assertTrue(content.contains("Moby-Dick"), content)
        assertTrue(content.contains("Whaling"), content)
        assertTrue(body?.get("system")?.jsonPrimitive?.content.orEmpty().contains("Finnish"))
    }

    /** The schema is what makes the answer parse without asking the model nicely. */
    @Test
    fun theAnswerIsConstrainedToASchema() = runTest {
        var body: JsonObject? = null
        val translator = translator(
            answers {
                body = it.jsonBody()
                ok()
            },
        )

        translator.translate(request())

        val format = body?.get("output_config")?.jsonObject?.get("format")?.jsonObject
        assertEquals("json_schema", format?.get("type")?.jsonPrimitive?.content)

        val properties = format?.get("schema")?.jsonObject?.get("properties")?.jsonObject
        assertTrue(properties?.containsKey("translation") == true, "$properties")
        assertTrue(properties?.containsKey("note") == true, "$properties")
    }

    /**
     * Effort is rejected outright by a model that does not take it, so it has to
     * be left off rather than sent hopefully.
     */
    @Test
    fun effortIsOnlySentToAModelThatTakesOne() = runTest {
        var body: JsonObject? = null
        val opus = translator(
            answers {
                body = it.jsonBody()
                ok()
            },
            model = "claude-opus-5",
        )
        opus.translate(request())

        assertEquals("low", body?.get("output_config")?.jsonObject?.get("effort")?.jsonPrimitive?.content)

        val haiku = translator(
            answers {
                body = it.jsonBody()
                ok()
            },
            model = "claude-haiku-4-5",
        )
        haiku.translate(request())

        assertNull(body?.get("output_config")?.jsonObject?.get("effort"))
    }

    /** Nothing is sent anywhere until the reader has actually set this up. */
    @Test
    fun withoutAKeyNothingIsSent() = runTest {
        var asked = false
        val translator = translator(
            answers {
                asked = true
                ok()
            },
            apiKey = null,
        )

        assertEquals(
            AppError.TranslationUnavailable,
            translator.failureFor(request()),
        )
        assertTrue(!asked, "a request was sent without a key")
    }

    /**
     * A rejected key and a busy service are the same HTTP failure here and
     * completely different problems to the person holding the phone.
     */
    @Test
    fun aRejectedKeySaysWhereToFixIt() = runTest {
        val translator = translator(answers { respondJson("{}", HttpStatusCode.Unauthorized) })

        val error = translator.failureFor(request()) as AppError.Unexpected
        assertTrue(error.cause.orEmpty().contains("Settings"), error.cause.orEmpty())
    }

    @Test
    fun beingRateLimitedSaysToWait() = runTest {
        val translator = translator(answers { respondJson("{}", HttpStatusCode.TooManyRequests) })

        val error = translator.failureFor(request()) as AppError.Unexpected
        assertTrue(error.cause.orEmpty().contains("Try again"), error.cause.orEmpty())
    }

    @Test
    fun aServiceOutageIsNotReportedAsTheReadersMistake() = runTest {
        val translator = translator(
            answers { respondJson("{}", HttpStatusCode.ServiceUnavailable) },
        )

        val error = translator.failureFor(request()) as AppError.Unexpected
        assertTrue(error.cause.orEmpty().contains("unavailable"), error.cause.orEmpty())
    }

    /** Anything else, the service explains better than this app can. */
    @Test
    fun theServicesOwnExplanationIsPassedOn() = runTest {
        val translator = translator(
            answers {
                respondJson(
                    """{"error": {"type": "invalid_request_error", "message": "max_tokens is too large"}}""",
                    HttpStatusCode.BadRequest,
                )
            },
        )

        assertEquals(
            AppError.Unexpected("max_tokens is too large"),
            translator.failureFor(request()),
        )
    }

    /**
     * A declined request comes back as a perfectly successful response with
     * nothing in it. Read as content it is a blank translation.
     */
    @Test
    fun aDeclinedRequestIsAFailureRatherThanAnEmptyTranslation() = runTest {
        val translator = translator(
            answers { respondStream(listOf(stopped("refusal"))) },
        )

        assertEquals(AppError.TranslationFailed, translator.failureFor(request()))
    }

    @Test
    fun anAnswerThatIsNotTheAgreedShapeIsAFailure() = runTest {
        val translator = translator(
            answers { respondStream(listOf(textDelta("Sorry!")) + stopped("end_turn")) },
        )

        assertEquals(AppError.TranslationFailed, translator.failureFor(request()))
    }

    /**
     * The answer as the service really sends it: a stream of text deltas that
     * spell out a JSON document, escaped inside the JSON of each event. Built
     * rather than written out, because a translation containing a quotation
     * mark is exactly the case worth covering and exactly the one hand-written
     * JSON gets wrong.
     *
     * Split into several deltas on purpose. An answer that arrived whole in one
     * event would pass without any of the joining being exercised, which is the
     * part of reading a stream that can actually be wrong.
     */
    private fun MockEngineScope.ok(
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

        return respondStream(passage.inThirds().map { textDelta(it) } + stopped(stopReason))
    }

    /** One `content_block_delta` carrying part of the answer. */
    private fun textDelta(text: String): String = json.encodeToString(
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
    private fun stopped(reason: String): String = json.encodeToString(
        buildJsonObject {
            put("type", "message_delta")
            put("delta", buildJsonObject { put("stop_reason", reason) })
        },
    )

    private fun translator(
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

        return AnthropicTranslator(client, settings, json)
    }

    private fun request(
        text: String = "Call me Ishmael.",
        context: TranslationContext = TranslationContext.None,
    ) = TranslationRequest(
        text = text,
        targetLanguage = TargetLanguage.Finnish,
        context = context,
    )

    private suspend fun AnthropicTranslator.failureFor(request: TranslationRequest): AppError =
        runCatching { translate(request) }.exceptionOrNull()?.toAppError()
            ?: error("expected a failure")

    private fun HttpRequestData.jsonBody(): JsonObject =
        json.parseToJsonElement((body as TextContent).text).jsonObject
}

private typealias MockEngineScope = io.ktor.client.engine.mock.MockRequestHandleScope

private fun answers(
    handle: MockEngineScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
) = MockEngine { request -> handle(request) }

/**
 * A streamed answer, in the shape both providers send one: a `data:` line per
 * event, a blank line between them, and `[DONE]` to close.
 */
private fun MockEngineScope.respondStream(events: List<String>) = respond(
    content = events.joinToString("") { "data: $it\n\n" } + "data: [DONE]\n\n",
    headers = headersOf("Content-Type", ContentType.Text.EventStream.toString()),
)

/** Three pieces, so joining them is actually exercised. */
private fun String.inThirds(): List<String> = chunked((length + 2) / 3)

private fun MockEngineScope.respondJson(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
) = respond(
    content = body.trimIndent(),
    status = status,
    headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
)
