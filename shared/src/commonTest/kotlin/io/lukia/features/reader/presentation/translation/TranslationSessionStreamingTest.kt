package io.lukia.features.reader.presentation.translation

import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.Translator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A translation arriving a token at a time, and the throttle that decides how
 * often the reader is shown how far it has got.
 *
 * The throttle is not a detail. `put` copies the whole map of translations, and
 * the screen above refiles every passage in the book each time it changes - so
 * publishing every token rebuilds the book's translations tens of times a
 * second, on the thread that draws, for a word or two of text. It is also
 * invisible when it breaks: everything still works, just badly, and the only
 * sign is somebody saying the page stutters.
 *
 * Assertable at all only because the session takes its clock as a parameter -
 * the scheduler below is the test's virtual one, so "eighty milliseconds later"
 * costs no time and never flakes.
 */
class TranslationSessionStreamingTest {

    private val passage = "Call me Ishmael."

    /**
     * Twenty tokens ten milliseconds apart is two hundred milliseconds of
     * answer, which at one showing per eighty is a handful - not twenty.
     */
    @Test
    fun anArrivingAnswerIsShownOnAClockRatherThanOnEveryToken() = runTest {
        val session = streaming(tokens = 20, apart = 10L)
        val shown = watch(session)

        session.tap(anchor, passage)
        advanceUntilIdle()

        assertTrue(shown.size in 2..5, "twenty tokens were shown as ${shown.size} rebuilds: $shown")
    }

    @Test
    fun theWholeTranslationIsWhatIsLeftOnThePage() = runTest {
        val session = streaming(tokens = 20, apart = 10L)

        session.tap(anchor, passage)
        advanceUntilIdle()

        assertIs<PassageTranslation.Ready>(session.passages.value[anchor])
    }

    /**
     * A whole answer arriving inside one throttle - twenty tokens a millisecond
     * apart, which is a short passage against a fast model - is shown once and
     * then replaced by the finished translation, rather than shown twenty times.
     *
     * The one it is shown as is the first, which is the piece that tells the
     * reader their tap landed on something.
     */
    @Test
    fun anAnswerThatArrivesInsideOneThrottleIsShownOnce() = runTest {
        val session = streaming(tokens = 20, apart = 1L)
        val shown = watch(session)

        session.tap(anchor, passage)
        advanceUntilIdle()

        assertEquals(listOf("1"), shown)
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
     * The first piece goes up at once: it is what tells the reader their tap
     * landed on something. It has to survive the throttle that keeps the pieces
     * behind it from filing every translation in the book once per token - see
     * `showAsItArrives` - which is a throttle that could very easily swallow
     * the one piece worth showing soonest.
     */
    @Test
    fun theFirstPieceOfAnArrivingAnswerIsShownAtOnce() = runTest {
        val answer = CompletableDeferred<Translation>()
        val session = TranslationSession(
            TranslatePassageUseCase(
                Translator { _, onPartial ->
                    onPartial("Kutsukaa")
                    answer.await()
                },
            ),
            this,
        )

        session.tap(anchor)
        runCurrent()

        assertEquals(PassageTranslation.Loading("Kutsukaa"), session.passages.value[anchor])

        // Let it finish, so the request is not left standing when the test ends.
        answer.complete(Translation("Kutsukaa minua Ismaeliksi."))
        advanceUntilIdle()
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
     * Every half-written sentence the passage was shown as, in order.
     *
     * Collected rather than sampled, because what is being asserted is how many
     * times the map was rebuilt - and the value it settled on says nothing about
     * that. Only the ones carrying text: the empty `Loading` that says the tap
     * landed is put by the tap itself, not by the throttle, and one collector
     * seeing it and another not is a fact about `StateFlow` conflation rather
     * than about the code under test.
     */
    private fun TestScope.watch(session: TranslationSession): List<String> {
        val seen = mutableListOf<String>()

        backgroundScope.launch {
            session.passages.collect { passages ->
                (passages[anchor] as? PassageTranslation.Loading)?.partial?.let { seen += it }
            }
        }

        return seen
    }

    /** A session whose answer arrives as [tokens] pieces, [apart] milliseconds apart. */
    private fun TestScope.streaming(tokens: Int, apart: Long) = TranslationSession(
        TranslatePassageUseCase(
            Translator { _, onPartial ->
                repeat(tokens) { index ->
                    onPartial((index + 1).toString())
                    delay(apart)
                }
                Translation("Kutsukaa minua Ismaeliksi.")
            },
        ),
        this,
        testScheduler.timeSource,
    )
}
