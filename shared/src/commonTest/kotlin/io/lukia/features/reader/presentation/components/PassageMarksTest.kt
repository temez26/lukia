package io.lukia.features.reader.presentation.components

import io.lukia.core.domain.AppError
import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.presentation.PassageTranslation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which passages get a highlight behind them.
 *
 * The interesting entry is the one that gets nothing: the reader asking for the
 * book's own words back is the reader reading, and a page that kept a mark for
 * every sentence anybody had ever tapped would be carrying a record of what they
 * had done rather than anything they could use.
 */
class PassageMarksTest {

    private val translation = Translation("Kutsukaa minua Ismaeliksi.")

    @Test
    fun anUntouchedPassageIsNotMarked() {
        assertNull(null.markStyle())
    }

    @Test
    fun aPassageStillArrivingIsMarkedAsWaiting() {
        assertEquals(PassageMarkStyle.Waiting, PassageTranslation.Loading().markStyle())
        assertEquals(PassageMarkStyle.Waiting, PassageTranslation.Loading("Kutsu").markStyle())
    }

    @Test
    fun aTranslationOnThePageIsMarked() {
        assertEquals(
            PassageMarkStyle.Translated,
            PassageTranslation.Ready(translation).markStyle()
        )
    }

    /** The whole point: nothing at all is drawn behind the book's own words. */
    @Test
    fun aPassageShowingTheOriginalIsNotMarked() {
        assertNull(
            PassageTranslation.Ready(translation, showingOriginal = true).markStyle()
        )
    }

    @Test
    fun aFailureIsMarkedAsOneSoItCanBeTappedAgain() {
        assertEquals(
            PassageMarkStyle.Failed,
            PassageTranslation.Failed(AppError.TranslationFailed).markStyle()
        )
    }
}
