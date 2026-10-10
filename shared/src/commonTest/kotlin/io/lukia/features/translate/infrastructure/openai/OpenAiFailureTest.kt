package io.lukia.features.translate.infrastructure.openai

import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpStatusCode
import io.lukia.core.domain.AppError
import io.lukia.features.translate.domain.provider.TranslationProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the reader is told when it goes wrong.
 *
 * Each of these is a case where the obvious reading of the response is the
 * wrong one: an account out of credit is not a busy server, and an answer cut
 * short is not malformed JSON.
 */
class OpenAiFailureTest {

    @Test
    fun aServerThatIsNotThereSaysWhereItWasLookedFor() = runTest {
        val translator = translator(
            MockEngine { throw kotlinx.io.IOException("Connection refused") },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234/v1",
            apiKey = null,
        )

        val error = translator.failureFor(request()) as AppError.Unexpected

        assertTrue(error.explanation.orEmpty().contains("http://localhost:1234/v1"), error.explanation.orEmpty())
    }

    @Test
    fun aRejectedKeySaysWhereToFixIt() = runTest {
        val translator = translator(answers { respondJson("{}", HttpStatusCode.Unauthorized) })

        val error = translator.failureFor(request()) as AppError.Unexpected
        assertTrue(error.explanation.orEmpty().contains("Settings"), error.explanation.orEmpty())
    }

    /** Pointing at the wrong path is the commonest mistake with a local server. */
    @Test
    fun nothingAnsweringAtTheAddressSaysSo() = runTest {
        val translator = translator(
            answers { respondJson("{}", HttpStatusCode.NotFound) },
            provider = TranslationProvider.Local,
            baseUrl = "http://localhost:1234",
            apiKey = null,
        )

        val error = translator.failureFor(request()) as AppError.Unexpected
        assertTrue(error.explanation.orEmpty().contains("/v1"), error.explanation.orEmpty())
    }

    /**
     * The other 404, and the one that used to be reported as the first.
     *
     * A server that answered and does not have that model is not a wrong
     * address, and sending somebody to correct an address that was right all
     * along is how a working setup gets taken apart looking for a fault that was
     * one field further down the page.
     */
    @Test
    fun aModelTheServerDoesNotHaveIsNotReportedAsAWrongAddress() = runTest {
        val translator = translator(
            answers {
                respondJson(
                    """{"error": {"message": "The model `gpt-9` does not exist", "code": "model_not_found"}}""",
                    HttpStatusCode.NotFound,
                )
            },
        )

        val explanation = (translator.failureFor(request()) as AppError.Unexpected)
            .explanation.orEmpty()

        assertTrue(explanation.contains("gpt-9"), explanation)
        assertTrue(!explanation.contains("/v1"), explanation)
    }

    /**
     * A server that accepted the request and then gave up on it says so on the
     * stream, where nothing used to be listening: the chunk carries no text, the
     * stream ends, and the reader was told their model had not answered with a
     * translation - which is true and is not what the server said.
     */
    @Test
    fun aServerThatGivesUpMidStreamIsQuotedRatherThanIgnored() = runTest {
        val translator = translator(
            answers {
                respondStream(
                    listOf(
                        contentChunk("""{"translation": "Kut"""),
                        """{"error": {"message": "the model was unloaded", "type": "server_error"}}""",
                    ),
                )
            },
        )

        assertEquals(
            AppError.Unexpected("the model was unloaded"),
            translator.failureFor(request()),
        )
    }

    /**
     * A connection dropped mid-answer ends a stream exactly as a finished one
     * does. Reported as a malformed answer it reads as the model's fault and is
     * never tried again; reported as what it is, it is the one failure here that
     * asking a second time actually fixes - see `RetryingTranslator`.
     */
    @Test
    fun anAnswerThatStoppedArrivingSaysSoRatherThanBlamingTheModel() = runTest {
        val translator = translator(
            answers { respondCutStream(listOf(contentChunk("""{"translation": "Kut"""))) },
        )

        val explanation = (translator.failureFor(request()) as AppError.Unexpected)
            .explanation.orEmpty()

        assertTrue(explanation.contains("stopped arriving"), explanation)
    }

    /** A server that does not have the model says so better than this code could. */
    @Test
    fun theServersOwnExplanationIsPassedOn() = runTest {
        val translator = translator(
            answers {
                respondJson(
                    """{"error": {"message": "model 'qwen' not found", "type": "invalid_request_error"}}""",
                    HttpStatusCode.BadRequest,
                )
            },
        )

        assertEquals(
            AppError.Unexpected("model 'qwen' not found"),
            translator.failureFor(request()),
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
                    HttpStatusCode.TooManyRequests,
                )
            },
        )

        val error = translator.failureFor(request()) as AppError.Unexpected

        assertTrue(error.explanation.orEmpty().contains("out of API credit"), error.explanation.orEmpty())
        assertTrue(!error.explanation.orEmpty().contains("Try again in a moment"), error.explanation.orEmpty())
    }

    /** A real rate limit still reads as one, in the service's own words. */
    @Test
    fun beingGenuinelyRateLimitedRepeatsWhatTheServiceSaid() = runTest {
        val translator = translator(
            answers {
                respondJson(
                    """{"error": {"message": "Rate limit reached for gpt-4.1", "type": "rate_limit_error"}}""",
                    HttpStatusCode.TooManyRequests,
                )
            },
        )

        assertEquals(
            AppError.Unexpected("Rate limit reached for gpt-4.1"),
            translator.failureFor(request()),
        )
    }

    /** With no explanation at all, the canned wording is still better than nothing. */
    @Test
    fun aBareRateLimitStillSaysSomethingUseful() = runTest {
        val translator = translator(answers { respondJson("{}", HttpStatusCode.TooManyRequests) })

        val error = translator.failureFor(request()) as AppError.Unexpected
        assertTrue(error.explanation.orEmpty().contains("Try again"), error.explanation.orEmpty())
    }

    /**
     * And says what actually ran out, which is usually not the passage: a model
     * that thinks before it answers is charged for the thinking too, so it names
     * the model and the thinking before it suggests a shorter passage.
     */
    @Test
    fun anAnswerCutShortSaysWhyRatherThanFailingToParse() = runTest {
        val translator = translator(
            answers { content("""{"translation": "Kut""", finishReason = "length") },
        )

        val error = translator.failureFor(request()) as AppError.Unexpected
        val explanation = error.explanation.orEmpty()

        assertTrue(explanation.contains("gpt-4o"), explanation)
        assertTrue(explanation.contains("thinking"), explanation)
    }
}
