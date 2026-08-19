package io.lukia.features.translate.infrastructure.openai

import io.ktor.client.request.HttpRequestData
import io.lukia.core.domain.AppError
import io.lukia.features.translate.domain.TranslationContext
import io.lukia.features.translate.domain.TranslationProvider
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
 * What actually goes out on the wire: where it is addressed, what carries the
 * key, how the prompt is laid out, and the one thing a hosted service can be
 * held to that somebody's laptop cannot - a schema.
 */
class OpenAiRequestTest {

    @Test
    fun theRequestGoesToTheChatEndpointUnderTheConfiguredAddress() = runTest {
        var sent: HttpRequestData? = null
        val translator = translator(
            answers {
                sent = it
                ok()
            },
        )

        translator.translate(request())

        assertEquals("https://api.openai.com/v1/chat/completions", sent?.url.toString())
    }

    /** A reader who types a trailing slash has not made a mistake. */
    @Test
    fun aTrailingSlashOnTheAddressIsNotADoubledOne() = runTest {
        var sent: HttpRequestData? = null
        val translator = translator(
            answers {
                sent = it
                ok()
            },
            baseUrl = "https://api.openai.com/v1/",
        )

        translator.translate(request())

        assertEquals("https://api.openai.com/v1/chat/completions", sent?.url.toString())
    }

    @Test
    fun theKeyIsSentAsABearerToken() = runTest {
        var sent: HttpRequestData? = null
        val translator = translator(
            answers {
                sent = it
                ok()
            },
            apiKey = "sk-test",
        )

        translator.translate(request())

        assertEquals("Bearer sk-test", sent?.headers?.get("Authorization"))
    }

    @Test
    fun thePromptTravelsAsASystemMessageAndAUserMessage() = runTest {
        var body: JsonObject? = null
        val translator = translator(
            answers {
                body = it.jsonBody()
                ok()
            },
        )

        translator.translate(
            request(context = TranslationContext(bookTitle = "Moby-Dick", genres = listOf("Whaling"))),
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
        val translator = translator(
            answers {
                body = it.jsonBody()
                ok()
            },
        )

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
            answers {
                body = it.jsonBody()
                ok()
            },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234/v1",
            apiKey = null,
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
            answers {
                sent = it
                ok()
            },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234/v1",
            apiKey = null,
        )

        translator.translate(request())

        assertNull(sent?.headers?.get("Authorization"))
    }

    /** One behind a proxy may want one, and giving one is not an error either. */
    @Test
    fun aLocalServerStillGetsAKeyWhenOneWasGiven() = runTest {
        var sent: HttpRequestData? = null
        val translator = translator(
            answers {
                sent = it
                ok()
            },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234/v1",
            apiKey = "behind-a-proxy",
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
    fun withoutAKeyOpenAiIsNotAskedAtAll() = runTest {
        var asked = false
        val translator = translator(
            answers {
                asked = true
                ok()
            },
            apiKey = null,
        )

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
            answers {
                asked = true
                ok()
            },
            provider = TranslationProvider.Local,
            baseUrl = "http://models.example.com/v1",
            apiKey = null,
        )

        val error = translator.failureFor(request()) as AppError.Unexpected

        assertTrue(error.cause.orEmpty().contains("plain HTTP"), error.cause.orEmpty())
        assertTrue(!asked, "a passage was sent in the clear")
    }

    /** The commonest state of a local server is not running. */
}
