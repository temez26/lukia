package io.lukia.features.translate.infrastructure.openai

import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpStatusCode
import io.lukia.core.domain.AppError
import io.lukia.features.translate.domain.provider.TranslationProvider
import io.lukia.features.translate.domain.translate
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
            apiKey = null,
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
        assertTrue(error.cause.orEmpty().contains("Try again"), error.cause.orEmpty())
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
        val cause = error.cause.orEmpty()

        assertTrue(cause.contains("gpt-4o"), cause)
        assertTrue(cause.contains("thinking"), cause)
    }
}
