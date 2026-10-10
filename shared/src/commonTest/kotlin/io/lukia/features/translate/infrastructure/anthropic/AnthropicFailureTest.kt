package io.lukia.features.translate.infrastructure.anthropic

import io.ktor.http.HttpStatusCode
import io.lukia.core.domain.AppError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the reader is told when the service will not answer.
 */
class AnthropicFailureTest {

    @Test
    fun aRejectedKeySaysWhereToFixIt() = runTest {
        val translator = translator(answers { respondJson("{}", HttpStatusCode.Unauthorized) })

        val error = translator.failureFor(request()) as AppError.Unexpected
        assertTrue(error.explanation.orEmpty().contains("Settings"), error.explanation.orEmpty())
    }

    @Test
    fun beingRateLimitedSaysToWait() = runTest {
        val translator = translator(answers { respondJson("{}", HttpStatusCode.TooManyRequests) })

        val error = translator.failureFor(request()) as AppError.Unexpected
        assertTrue(error.explanation.orEmpty().contains("Try again"), error.explanation.orEmpty())
    }

    @Test
    fun aServiceOutageIsNotReportedAsTheReadersMistake() = runTest {
        val translator = translator(
            answers { respondJson("{}", HttpStatusCode.ServiceUnavailable) },
        )

        val error = translator.failureFor(request()) as AppError.Unexpected
        assertTrue(error.explanation.orEmpty().contains("unavailable"), error.explanation.orEmpty())
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
}
