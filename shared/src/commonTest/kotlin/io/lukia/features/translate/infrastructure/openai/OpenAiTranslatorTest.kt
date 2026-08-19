package io.lukia.features.translate.infrastructure.openai

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.lukia.core.domain.AppError
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.translate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    /** Nothing is worth showing until the model has begun the sentence itself. */
    @Test
    fun aModelStillIntroducingItselfReportsNothing() = runTest {
        val translator = translator(
            answers { content("Sure! Here you go:") },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234/v1",
            apiKey = null,
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
            model = "qwen2.5-7b-instruct",
        )

        val error = translator.failureFor(request()) as AppError.Unexpected
        val cause = error.cause.orEmpty()

        assertTrue(cause.contains("qwen2.5-7b-instruct"), cause)
        assertTrue(cause.contains("still loading"), cause)
        assertTrue(!cause.contains("server is running"), cause)
    }

    @Test
    fun anAnswerWrappedInProseIsStillRead() = runTest {
        val translator = translator(
            answers {
                content(
                    "Sure! Here is the translation:\n" +
                        "```json\n{\"translation\": \"Hei.\", \"note\": \"\"}\n```\n" +
                        "Let me know if you need anything else.",
                )
            },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234/v1",
            apiKey = null,
        )

        assertEquals("Hei.", translator.translate(request()).text)
    }

    /** An answer with no object in it at all is a model that did not comply. */
    @Test
    fun proseWithNoAnswerInItIsAFailure() = runTest {
        val translator = translator(answers { content("I am unable to help with that.") })

        assertEquals(AppError.TranslationFailed, translator.failureFor(request()))
    }
}
