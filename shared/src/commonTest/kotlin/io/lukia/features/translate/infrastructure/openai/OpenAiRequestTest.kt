package io.lukia.features.translate.infrastructure.openai

import io.ktor.client.request.HttpRequestData
import io.lukia.core.domain.AppError
import io.lukia.features.translate.domain.TranslationContext
import io.lukia.features.translate.domain.provider.TranslationProvider
import io.lukia.features.translate.domain.translate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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

    /**
     * The reason paragraphs would not translate while sentences would.
     *
     * `max_tokens` is a ceiling on the model's thinking *and* its answer, and a
     * model that thinks is what a reader now runs locally. Measured against one:
     * a sentence stopped on its own after 31 tokens, while a paragraph spent
     * three thousand characters thinking and was cut off mid-answer. Held at a
     * flat 1024 the paragraph could never get under it, and the reader was told
     * their passage was too long by a model that had translated it fine.
     *
     * So every passage, down to the shortest, is allowed room to be thought
     * about - which is what this pins.
     */
    @Test
    fun everyPassageIsAllowedRoomToThinkIn() = runTest {
        var sentence: HttpRequestData? = null
        var paragraph: HttpRequestData? = null

        translator(
            answers {
                sentence = it
                ok()
            },
        ).translate(request())
        translator(
            answers {
                paragraph = it
                ok()
            },
        ).translate(request(text = LONG_PASSAGE))

        assertTrue(sentence.maxTokens() >= 4096, "a sentence had ${sentence.maxTokens()}")
        assertTrue(paragraph.maxTokens() >= 4096, "a paragraph had ${paragraph.maxTokens()}")
    }

    /** Past that, the room follows the passage: a long one needs a long answer. */
    @Test
    fun aPassageLongerThanTheFloorIsAllowedMoreThanIt() = runTest {
        var sent: HttpRequestData? = null

        translator(
            answers {
                sent = it
                ok()
            },
        ).translate(request(text = LONG_PASSAGE.repeat(8)))

        assertTrue(sent.maxTokens() > 4096, "a long passage was allowed only ${sent.maxTokens()}")
    }

    /** A model that has stopped translating and started rambling is still stopped. */
    @Test
    fun noPassageIsAllowedRoomWithoutEnd() = runTest {
        var sent: HttpRequestData? = null

        translator(
            answers {
                sent = it
                ok()
            },
        )
            .translate(request(text = LONG_PASSAGE.repeat(50)))

        assertTrue(sent.maxTokens() <= 8192, "one passage was allowed ${sent.maxTokens()}")
    }

    /**
     * The hosted service deprecated `max_tokens` and its current models reject
     * it outright, while nearly every server somebody runs at home implements
     * that name and not the new one. Sending the wrong one is not a degraded
     * answer, it is a 400 - so which name is used is decided by who is being
     * asked rather than picked once.
     */
    @Test
    fun theCeilingIsSentUnderTheNameTheServerUnderstands() = runTest {
        var hosted: HttpRequestData? = null
        var local: HttpRequestData? = null

        translator(
            answers {
                hosted = it
                ok()
            },
        ).translate(request())

        translator(
            answers {
                local = it
                ok()
            },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234/v1",
            apiKey = null,
        ).translate(request())

        assertNotNull(hosted?.jsonBody()?.get("max_completion_tokens"))
        assertNull(hosted?.jsonBody()?.get("max_tokens"), "the deprecated name was sent as well")

        assertNotNull(local?.jsonBody()?.get("max_tokens"))
        assertNull(
            local?.jsonBody()?.get("max_completion_tokens"),
            "a local server was sent a name most of them do not implement",
        )
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

        assertTrue(error.explanation.orEmpty().contains("plain HTTP"), error.explanation.orEmpty())
        assertTrue(!asked, "a passage was sent in the clear")
    }

    /** The commonest state of a local server is not running. */
}

/**
 * A paragraph of a real book, which is the length that stopped working - see
 * `aParagraphIsAllowedMoreRoomThanASentence`.
 */
private const val LONG_PASSAGE =
    "What Tisquantum saw on his return home was unimaginable. From southern Maine to " +
        "Narragansett Bay, the coast was empty. What had once been a line of busy " +
        "communities was now a mass of tumbledown homes and untended fields overrun by " +
        "blackberries. Scattered among the houses and fields were skeletons bleached by " +
        "the sun. Patuxet had been hit with special force. Not a single person remained."

/**
 * The allowance, under whichever of its two names this request used - see
 * `theCeilingIsSentUnderTheNameTheServerUnderstands` for which is which.
 */
private fun HttpRequestData?.maxTokens(): Int {
    val body = this?.jsonBody()

    return (body?.get("max_completion_tokens") ?: body?.get("max_tokens"))
        ?.jsonPrimitive?.content?.toInt()
        ?: error("no token allowance was sent")
}
