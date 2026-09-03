package io.lukia.features.translate.presentation

import io.lukia.core.domain.AppError
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.Translator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A passage the reader points at: what it says while it waits, what it says
 * when it fails, and how tapping it again flips between the translation and
 * the book's own words.
 */
class TranslationSessionTest {

    @Test
    fun anUntouchedPassageIsTranslatedOnTheFirstTap() = runTest {
        val session = session { Translation("Kutsukaa minua Ismaeliksi.") }

        session.tap(anchor)
        runCurrent()

        assertEquals(
            "Kutsukaa minua Ismaeliksi.",
            (session.passages.value[anchor] as PassageTranslation.Ready).translation.text,
        )
    }

    @Test
    fun aPassageBeingTranslatedSaysSoWhileItWaits() = runTest {
        val answer = CompletableDeferred<Translation>()
        val session = session { answer.await() }

        session.tap(anchor)
        runCurrent()

        assertEquals(PassageTranslation.Loading(), session.passages.value[anchor])

        answer.complete(Translation("Kutsukaa minua Ismaeliksi."))
        runCurrent()

        assertIs<PassageTranslation.Ready>(session.passages.value[anchor])
    }

    /** The whole point of the feature: back to the original and forward again. */
    @Test
    fun tappingATranslatedPassageFlipsItBackAndForth() = runTest {
        val session = session { Translation("Kutsukaa minua Ismaeliksi.") }

        session.tap(anchor)
        runCurrent()
        assertEquals(false, session.ready(anchor).showingOriginal)

        session.tap(anchor)
        assertEquals(true, session.ready(anchor).showingOriginal)

        session.tap(anchor)
        assertEquals(false, session.ready(anchor).showingOriginal)
    }

    /**
     * Flipping is not re-translating. A reader comparing two renderings of the
     * same line should not pay for the second one - or wait for it.
     */
    @Test
    fun flippingBackAndForthAsksTheTranslatorOnlyOnce() = runTest {
        var asked = 0
        val session = session {
            asked++
            Translation("Kutsukaa minua Ismaeliksi.")
        }

        session.tap(anchor)
        runCurrent()
        repeat(4) { session.tap(anchor) }
        runCurrent()

        assertEquals(1, asked)
    }

    /**
     * Tapping again while it is still coming back is somebody wondering whether
     * their first tap landed, not a second request.
     */
    @Test
    fun tappingWhileItIsStillLoadingDoesNotAskAgain() = runTest {
        val answer = CompletableDeferred<Translation>()
        var asked = 0
        val session = session {
            asked++
            answer.await()
        }

        session.tap(anchor)
        runCurrent()
        session.tap(anchor)
        session.tap(anchor)
        runCurrent()

        assertEquals(1, asked)
        assertEquals(PassageTranslation.Loading(), session.passages.value[anchor])

        answer.complete(Translation("Done."))
    }

    @Test
    fun aFailureIsRememberedSoTheReaderCanSeeIt() = runTest {
        val session = session { throw RuntimeException("no network") }

        session.tap(anchor)
        runCurrent()

        assertEquals(
            AppError.Unexpected(),
            (session.passages.value[anchor] as PassageTranslation.Failed).error,
        )
    }

    /** Tapping a failed passage is how somebody retries it. */
    @Test
    fun tappingAFailedPassageTriesAgain() = runTest {
        var attempt = 0
        val session = session {
            attempt++
            if (attempt == 1) throw RuntimeException("no network") else Translation("Second time.")
        }

        session.tap(anchor)
        runCurrent()
        assertIs<PassageTranslation.Failed>(session.passages.value[anchor])

        session.tap(anchor)
        runCurrent()

        assertEquals("Second time.", session.ready(anchor).translation.text)
        assertEquals(2, attempt)
    }

    @Test
    fun aFlipCostsNothingToPrepare() = runTest {
        var built = 0
        val session = session { Translation("Kutsukaa minua Ismaeliksi.") }

        fun tap() = session.onPassageTapped(anchor) {
            built++
            request("Call me Ishmael.")
        }

        tap()
        runCurrent()
        tap()
        tap()

        assertEquals(1, built)
    }

    /**
     * What the reader sees while a model is writing: the sentence filling in,
     * rather than the original sitting there looking untouched.
     */
    @Test
    fun aSentenceStillArrivingIsShownAsFarAsItHasGot() = runTest {
        val session = TranslationSession(
            TranslatePassageUseCase(
                Translator { _, onPartial ->
                    onPartial("Kutsukaa")
                    onPartial("Kutsukaa minua")
                    Translation("Kutsukaa minua Ismaeliksi.")
                },
            ),
            this,
        )

        session.tap(anchor)
        runCurrent()

        assertEquals(
            PassageTranslation.Ready(Translation("Kutsukaa minua Ismaeliksi.")),
            session.passages.value[anchor],
        )
    }

    /**
     * A piece still in flight must not land on top of the finished answer -
     * which is the one way a streamed translation could end up permanently
     * showing half of itself.
     */
    @Test
    fun theLastThingShownIsTheWholeTranslation() = runTest {
        val session = TranslationSession(
            TranslatePassageUseCase(
                Translator { _, onPartial ->
                    repeat(20) { onPartial("Kutsukaa".take(it + 1)) }
                    Translation("Kutsukaa minua Ismaeliksi.")
                },
            ),
            this,
        )

        session.tap(anchor)
        advanceUntilIdle()

        assertEquals(
            "Kutsukaa minua Ismaeliksi.",
            (session.passages.value[anchor] as PassageTranslation.Ready).translation.text,
        )
    }

    /**
     * Translating a whole page must not undo what the reader has already done to
     * it: a sentence they flipped back to the original stays as they left it,
     * and one that failed is not retried behind their back forty times over.
     */
}
