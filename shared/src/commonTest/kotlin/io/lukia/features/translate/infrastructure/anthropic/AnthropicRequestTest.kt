package io.lukia.features.translate.infrastructure.anthropic

import io.ktor.client.request.HttpRequestData
import io.lukia.core.domain.AppError
import io.lukia.features.translate.domain.TranslationContext
import io.lukia.features.translate.domain.translate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What goes out on the wire: the key and the API version, which model is asked,
 * what the book contributes to the prompt, and the schema the answer is held to.
 */
class AnthropicRequestTest {

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
}
