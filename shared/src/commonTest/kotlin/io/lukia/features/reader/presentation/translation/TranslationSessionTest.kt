package io.lukia.features.reader.presentation.translation

import io.lukia.core.domain.AppError
import io.lukia.features.translate.domain.Translation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * A passage the reader points at: what it says while it waits, what it says
 * when it fails, and how tapping it again flips between the translation and
 * the book's own words.
 *
 * An answer that arrives a piece at a time is [TranslationSessionStreamingTest]
 * next door, and putting a whole page away is [TranslationSessionVisibilityTest].
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

    /**
     * Tapping a failed passage takes the error off the page, without asking a
     * service that may only fail again and put it straight back.
     */
    @Test
    fun tappingAFailedPassagePutsTheFailureAway() = runTest {
        var attempt = 0
        val session = session {
            attempt++
            throw RuntimeException("no network")
        }

        session.tap(anchor)
        runCurrent()
        assertIs<PassageTranslation.Failed>(session.passages.value[anchor])

        session.tap(anchor)
        runCurrent()

        assertNull(session.passages.value[anchor], "the failure was left on the page")
        assertEquals(1, attempt)
    }

    /** And the tap after that finds an untouched passage, which is the retry. */
    @Test
    fun tappingAPassageWhoseFailureWasPutAwayTriesAgain() = runTest {
        var attempt = 0
        val session = session {
            attempt++
            if (attempt == 1) throw RuntimeException("no network") else Translation("Second time.")
        }

        session.tap(anchor)
        runCurrent()
        session.tap(anchor)
        session.tap(anchor)
        runCurrent()

        assertEquals("Second time.", session.ready(anchor).translation.text)
        assertEquals(2, attempt)
    }

    /** The word panel's button says "Try again" over a failure, and does. */
    @Test
    fun actioningAFailedPassageTriesAgain() = runTest {
        var attempt = 0
        val session = session {
            attempt++
            if (attempt == 1) throw RuntimeException("no network") else Translation("Second time.")
        }

        session.tap(anchor)
        runCurrent()

        session.onPassageActioned(anchor) { request("Call me Ishmael.") }
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
}
