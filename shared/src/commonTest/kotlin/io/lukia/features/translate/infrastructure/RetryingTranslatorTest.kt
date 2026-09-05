package io.lukia.features.translate.infrastructure

import io.lukia.core.domain.AppError
import io.lukia.core.domain.AppErrorException
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.translate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Which failures are worth a second attempt, and which are just a slower way of
 * arriving at the same sentence.
 *
 * The distinction is the whole of this decorator: a rate limit is over in
 * seconds and a rejected key never is, and asking again is either the right
 * answer or a waste of somebody's battery depending only on which of them it
 * was.
 */
class RetryingTranslatorTest {

    @Test
    fun aRateLimitIsAskedAgain() = runTest {
        val translator = failing(times = 1, kind = TranslationFailureKind.RateLimited)

        assertEquals("Hei.", retrying(translator).translate(request()).text)
        assertEquals(2, translator.attempts)
    }

    @Test
    fun aBusyServiceIsAskedAgain() = runTest {
        val translator = failing(times = 1, kind = TranslationFailureKind.ServiceUnavailable)

        assertEquals("Hei.", retrying(translator).translate(request()).text)
    }

    /** A connection dropped mid-answer is the failure a second attempt fixes. */
    @Test
    fun anAnswerThatStoppedArrivingIsAskedAgain() = runTest {
        val translator = failing(times = 1, kind = TranslationFailureKind.CutShort)

        assertEquals("Hei.", retrying(translator).translate(request()).text)
    }

    @Test
    fun aRejectedKeyIsNotAskedAgain() = runTest {
        val translator = failing(times = 1, kind = TranslationFailureKind.KeyRejected)

        retrying(translator).failure()

        assertEquals(1, translator.attempts, "a key that was refused was offered again")
    }

    @Test
    fun anAccountWithNoCreditIsNotAskedAgain() = runTest {
        val translator = failing(times = 1, kind = TranslationFailureKind.NoCredit)

        retrying(translator).failure()

        assertEquals(1, translator.attempts)
    }

    @Test
    fun aModelThatDoesNotExistIsNotAskedAgain() = runTest {
        val translator = failing(times = 1, kind = TranslationFailureKind.ModelNotFound)

        retrying(translator).failure()

        assertEquals(1, translator.attempts)
    }

    /** A passage the model declined is declined just as firmly the second time. */
    @Test
    fun aRefusedPassageIsNotAskedAgain() = runTest {
        val translator = failing(times = 1, kind = TranslationFailureKind.Refused)

        retrying(translator).failure()

        assertEquals(1, translator.attempts)
    }

    /** Anything the app raises that is not one of these is left exactly as it is. */
    @Test
    fun aFailureFromSomewhereElseIsNotAskedAgain() = runTest {
        var attempts = 0
        val translator = Translator { _, _ ->
            attempts++
            throw AppErrorException(AppError.TranslationUnavailable)
        }

        assertEquals(AppError.TranslationUnavailable, retrying(translator).failure())
        assertEquals(1, attempts)
    }

    @Test
    fun givingUpReportsTheLastFailureRatherThanOneOfItsOwn() = runTest {
        val translator = failing(times = 9, kind = TranslationFailureKind.RateLimited)

        assertEquals(AppError.Unexpected("busy"), retrying(translator).failure())
        assertEquals(3, translator.attempts, "the ceiling on attempts was not held")
    }

    /**
     * The contract the reader sees: what is shown is only ever a prefix of what
     * is coming. A second attempt starts its answer from nothing, so retrying
     * after half a sentence is on the page would take those words back and write
     * them again.
     */
    @Test
    fun anAnswerAlreadyOnThePageIsNotStartedOver() = runTest {
        var attempts = 0
        val translator = Translator { _, onPartial ->
            attempts++
            onPartial("Kutsu")
            throw TranslationFailureException(TranslationFailureKind.CutShort, "stopped")
        }

        retrying(translator).failure()

        assertEquals(1, attempts, "a half-written sentence was written twice")
    }

    /**
     * The service is the only party that knows when its own limit resets, so
     * what it asked for is what is waited - not a backoff invented here.
     */
    @Test
    fun aServiceThatSaidHowLongToWaitIsWaitedFor() = runTest {
        val waits = mutableListOf<Duration>()
        val translator = failing(
            times = 1,
            kind = TranslationFailureKind.RateLimited,
            retryAfterSeconds = 2,
        )

        RetryingTranslator(translator, wait = { waits += it }).translate(request())

        assertEquals(listOf(2.seconds), waits)
    }

    /**
     * And is not waited for past what the reader would sit through. A sentence
     * that arrives a minute after the tap is one they gave up on long ago.
     */
    @Test
    fun aWaitLongerThanTheReaderWouldSitThroughIsNotWaited() = runTest {
        val translator = failing(
            times = 1,
            kind = TranslationFailureKind.RateLimited,
            retryAfterSeconds = 300,
        )

        retrying(translator).failure()

        assertEquals(1, translator.attempts)
    }

    /**
     * Without one, the wait grows and is cut by a random fraction - which is
     * what keeps a screenful of passages that failed together from all coming
     * back together and renewing the limit that stopped them.
     */
    @Test
    fun theWaitGrowsWithEachAttemptAndIsNeverTheSameForEverybody() = runTest {
        val waits = mutableListOf<Duration>()
        val translator = failing(times = 9, kind = TranslationFailureKind.ServiceUnavailable)

        RetryingTranslator(translator, wait = { waits += it }, jitter = { 1.0 }).failure()

        assertEquals(2, waits.size)
        assertTrue(waits[1] > waits[0], "the second wait was not longer: $waits")
    }

    private fun retrying(translator: Translator) = RetryingTranslator(translator, wait = {})

    private suspend fun Translator.failure(): AppError =
        runCatching { translate(request()) }.exceptionOrNull()?.let {
            (it as AppErrorException).error
        } ?: error("expected a failure")

    private fun request() = TranslationRequest(
        text = "Call me Ishmael.",
        targetLanguage = TargetLanguage.Finnish,
    )

    /** Fails [times] times over and then answers, counting how often it was asked. */
    private fun failing(
        times: Int,
        kind: TranslationFailureKind,
        retryAfterSeconds: Int? = null,
    ) = object : Translator {
        var attempts = 0
            private set

        override suspend fun translate(
            request: TranslationRequest,
            onPartial: (String) -> Unit,
        ): Translation {
            attempts++

            if (attempts <= times) {
                throw TranslationFailureException(kind, "busy", retryAfterSeconds)
            }

            return Translation(text = "Hei.")
        }
    }
}
