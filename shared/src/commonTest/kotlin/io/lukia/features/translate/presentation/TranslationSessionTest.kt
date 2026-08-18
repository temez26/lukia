package io.lukia.features.translate.presentation

import io.lukia.core.domain.AppError
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TextAnchor
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranslationSessionTest {

    private val anchor = TextAnchor.Section(sectionIndex = 0, blockIndex = 1, sentenceIndex = 2)

    @Test
    fun anUntouchedPassageIsTranslatedOnTheFirstTap() = runTest {
        val session = session { Translation("Kutsukaa minua Ismaeliksi.") }

        session.tap(anchor)
        runCurrent()

        assertEquals(
            "Kutsukaa minua Ismaeliksi.",
            (session.passages.value[anchor] as PassageTranslation.Ready).translation.text
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
            AppError.Unexpected("no network"),
            (session.passages.value[anchor] as PassageTranslation.Failed).error
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
    fun passagesAreKeptApartByWhereTheyAre() = runTest {
        val other = TextAnchor.Section(sectionIndex = 0, blockIndex = 1, sentenceIndex = 3)
        val session = session { Translation("Translated ${it.text}") }

        session.tap(anchor, passage = "First.")
        session.tap(other, passage = "Second.")
        runCurrent()

        assertEquals("Translated First.", session.ready(anchor).translation.text)
        assertEquals("Translated Second.", session.ready(other).translation.text)
    }

    /**
     * A page's sentence and a section's sentence carrying the same numbers are
     * not the same sentence.
     */
    @Test
    fun aPageAnchorIsNotASectionAnchor() = runTest {
        val page = TextAnchor.Page(pageIndex = 0, sentenceIndex = 2)
        val session = session { Translation("Translated.") }

        session.tap(page)
        runCurrent()

        assertTrue(session.passages.value.containsKey(page))
        assertNull(session.passages.value[TextAnchor.Section(0, 0, 2)])
    }

    /**
     * A long evening with a long book cannot end with every tap copying every
     * translation made since it was opened.
     */
    @Test
    fun onlySoManyPassagesAreKeptAtOnce() = runTest {
        val session = session { Translation("Translated ${it.text}") }

        repeat(500) { index -> session.tap(anchor(index)) }
        runCurrent()

        assertEquals(400, session.passages.value.size)
    }

    /** What falls out is the part of the book furthest behind the reader. */
    @Test
    fun theOldestPassageIsTheOneForgotten() = runTest {
        val session = session { Translation("Translated ${it.text}") }

        repeat(401) { index -> session.tap(anchor(index)) }
        runCurrent()

        assertNull(session.passages.value[anchor(0)])
        assertIs<PassageTranslation.Ready>(session.passages.value[anchor(400)])
    }

    /** Flipping a passage is touching it, so it is not the next one dropped. */
    @Test
    fun aPassageTheReaderKeepsComingBackToIsKept() = runTest {
        val session = session { Translation("Translated ${it.text}") }

        session.tap(anchor(0))
        runCurrent()

        repeat(399) { index -> session.tap(anchor(index + 1)) }
        runCurrent()

        // Flipping it to the original is a touch, and moves it to the front.
        session.tap(anchor(0))
        session.tap(anchor(500))
        runCurrent()

        assertIs<PassageTranslation.Ready>(session.passages.value[anchor(0)])
        assertNull(session.passages.value[anchor(1)])
    }

    @Test
    fun clearingForgetsEverythingTranslatedSoFar() = runTest {
        val session = session { Translation("Kutsukaa minua Ismaeliksi.") }

        session.tap(anchor)
        runCurrent()
        session.clear()

        assertTrue(session.passages.value.isEmpty())
    }

    /** The request is only built when there is actually something to ask. */
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
                }
            ),
            this
        )

        session.tap(anchor)
        runCurrent()

        assertEquals(
            PassageTranslation.Ready(Translation("Kutsukaa minua Ismaeliksi.")),
            session.passages.value[anchor]
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
                }
            ),
            this
        )

        session.tap(anchor)
        advanceUntilIdle()

        assertEquals(
            "Kutsukaa minua Ismaeliksi.",
            (session.passages.value[anchor] as PassageTranslation.Ready).translation.text
        )
    }

    private fun TestScope.session(
        translate: suspend (TranslationRequest) -> Translation
    ) = TranslationSession(
        TranslatePassageUseCase(Translator { request, _ -> translate(request) }),
        this
    )

    private fun anchor(sentenceIndex: Int) =
        TextAnchor.Section(sectionIndex = 0, blockIndex = 0, sentenceIndex = sentenceIndex)

    private fun TranslationSession.tap(
        anchor: TextAnchor,
        passage: String = "Call me Ishmael."
    ) = onPassageTapped(anchor) { request(passage) }

    private fun TranslationSession.ready(anchor: TextAnchor) =
        passages.value[anchor] as PassageTranslation.Ready

    private fun request(passage: String) = TranslationRequest(
        text = passage,
        targetLanguage = TargetLanguage.Finnish
    )
}
