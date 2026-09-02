package io.lukia.features.translate.infrastructure.anthropic

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.serialization.kotlinx.json.json
import io.lukia.core.domain.AppError
import io.lukia.features.translate.domain.translate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
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
        val explanation = error.explanation.orEmpty()

        assertTrue(explanation.contains("did not answer"), explanation)
        assertTrue(explanation.contains("Settings"), explanation)
    }

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
}
