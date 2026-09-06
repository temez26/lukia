package io.lukia.features.reader.presentation.translation

import io.lukia.features.translate.domain.Translation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Asking for passages rather than tapping them, and putting every translation
 * in the book away at once - which must leave a passage that is still arriving
 * alone.
 */
class TranslationSessionVisibilityTest {

    @Test
    fun askingForAPassageLeavesOneAlreadyAnsweredExactlyAsItWas() = runTest {
        var asked = 0
        val session = session {
            asked++
            Translation("Kutsukaa minua Ismaeliksi.")
        }

        session.tap(anchor)
        runCurrent()
        session.tap(anchor)

        val flipped = session.ready(anchor)
        assertTrue(flipped.showingOriginal, "the sentence was not flipped back")

        session.onPassageRequested(anchor) { request("Call me Ishmael.") }
        runCurrent()

        assertEquals(1, asked, "a passage already answered was asked about again")
        assertTrue(session.ready(anchor).showingOriginal, "the reader's own choice was undone")
    }

    @Test
    fun askingForAPassageNobodyHasTouchedTranslatesIt() = runTest {
        val session = session { Translation("Kutsukaa minua Ismaeliksi.") }

        session.onPassageRequested(anchor) { request("Call me Ishmael.") }
        runCurrent()

        assertEquals("Kutsukaa minua Ismaeliksi.", session.ready(anchor).translation.text)
    }

    /**
     * Putting the translations away is how somebody reads a stretch they have
     * already worked through in the book's own words. The answers stay - they
     * are still paid for, and still one press from being back.
     */
    @Test
    fun hidingTheTranslationsPutsEveryOneOfThemBackToTheBooksWords() = runTest {
        val session = session { Translation("Kutsukaa minua Ismaeliksi.") }

        session.tap(anchor(0))
        session.tap(anchor(1))
        runCurrent()

        session.showTranslations(false)

        assertTrue(session.ready(anchor(0)).showingOriginal)
        assertTrue(session.ready(anchor(1)).showingOriginal)
        assertEquals("Kutsukaa minua Ismaeliksi.", session.ready(anchor(0)).translation.text)
    }

    @Test
    fun showingThemAgainBringsBackEveryOneOfThem() = runTest {
        val session = session { Translation("Kutsukaa minua Ismaeliksi.") }

        session.tap(anchor(0))
        session.tap(anchor(1))
        runCurrent()
        session.showTranslations(false)

        session.showTranslations(true)

        assertFalse(session.ready(anchor(0)).showingOriginal)
        assertFalse(session.ready(anchor(1)).showingOriginal)
    }

    /**
     * A passage still arriving has nothing to show or to hide, and one that
     * failed has nothing either - neither is a translation yet.
     */
    @Test
    fun hidingLeavesAPassageThatIsStillArrivingAlone() = runTest {
        val answer = CompletableDeferred<Translation>()
        val session = session { answer.await() }

        session.tap(anchor)
        runCurrent()

        session.showTranslations(false)

        assertIs<PassageTranslation.Loading>(session.passages.value[anchor])

        // Let it land, so the test does not end with a request still in flight.
        answer.complete(Translation("Kutsukaa minua Ismaeliksi."))
        runCurrent()
    }

    /** Nothing translated, nothing to change - and nothing reported as changed. */
    @Test
    fun hidingWithNothingTranslatedChangesNothing() = runTest {
        val session = session { Translation("Kutsukaa minua Ismaeliksi.") }

        session.showTranslations(false)

        assertTrue(session.passages.value.isEmpty())
    }
}
