package io.lukia.features.translate.infrastructure

import io.lukia.core.domain.storage.FakeSecretStore
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.core.domain.text.PassageUnit
import io.lukia.features.translate.domain.settings.TranslationGestures
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.domain.settings.WordLookup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a thumb on the page is set to do, across closing the book.
 *
 * Apart from [TranslationSettingsRepositoryTest], which is about the machinery -
 * addresses, keys, models, waits, and one provider's settings not becoming
 * another's. These are the settings a reader actually meets, and there are
 * enough of them to be their own page: everything here starts off, the tap is
 * two settings written to two keys, and an install from before that split has to
 * keep meaning what it meant.
 */
class TranslationGesturePersistenceTest {

    private val secrets = FakeSecretStore()

    @Test
    fun whatTheGesturesDoIsThereTheNextTimeTheAppOpens() {
        val store = FakeSettingsStore()

        TranslationSettingsRepository(store, secrets).update(
            TranslationSettings().withGestures(
                TranslationGestures.of(tap = PassageUnit.Paragraph, longPress = PassageUnit.Sentence),
            ),
        )

        val reopened = TranslationSettingsRepository(store, secrets).settings.value

        assertEquals(PassageUnit.Paragraph, reopened.gestures.tap)
        assertEquals(PassageUnit.Sentence, reopened.gestures.longPress)
    }

    /**
     * Off until it is asked for, like every other thing translating does - and
     * on again the next time the app opens once it has been.
     */
    @Test
    fun holdingAWordForMoreAboutItIsOffUntilItIsTurnedOn() {
        val store = FakeSettingsStore()

        assertFalse(
            TranslationSettingsRepository(store, secrets).settings.value.gestures.wordDetails,
        )

        TranslationSettingsRepository(store, secrets).update(
            TranslationSettings().withGestures(TranslationGestures.of(wordDetails = true)),
        )

        val reopened = TranslationSettingsRepository(store, secrets).settings.value

        assertTrue(reopened.gestures.wordDetails)
    }

    @Test
    fun notesUnderTheParagraphAreOffUntilTheyAreTurnedOn() {
        val store = FakeSettingsStore()

        assertFalse(TranslationSettingsRepository(store, secrets).settings.value.notes)

        TranslationSettingsRepository(store, secrets).update(
            TranslationSettings().withNotes(true),
        )

        assertTrue(TranslationSettingsRepository(store, secrets).settings.value.notes)
    }

    /**
     * The unit a tap asks for outlives the tap being switched off, because the
     * two are separate settings set on separate pages - see
     * [TranslationGestures.tapTranslates]. A reader who raised their tap to the
     * paragraph and then turned tapping off for an evening should not find it
     * back at the word in the morning.
     */
    @Test
    fun whatATapWouldAskForSurvivesTheTapBeingTurnedOff() {
        val store = FakeSettingsStore()

        TranslationSettingsRepository(store, secrets).update(
            TranslationSettings().withGestures(
                TranslationGestures(tapTranslates = false, tapUnit = PassageUnit.Paragraph),
            ),
        )

        val reopened = TranslationSettingsRepository(store, secrets).settings.value

        assertNull(reopened.gestures.tap, "a tap nobody turned on translated something")
        assertEquals(PassageUnit.Paragraph, reopened.gestures.tapUnit)
    }

    /**
     * An install written before the tap was two settings has only the one value,
     * and whatever it asked for is what it asks for - both halves of it.
     */
    @Test
    fun aTapStoredBeforeTheSplitKeepsWhatItAskedFor() {
        val store = FakeSettingsStore()
        store.putString("translation.gesture.tap", PassageUnit.Sentence.name)

        val reopened = TranslationSettingsRepository(store, secrets).settings.value

        assertEquals(PassageUnit.Sentence, reopened.gestures.tap)
        assertEquals(PassageUnit.Sentence, reopened.gestures.tapUnit)
        assertTrue(reopened.gestures.tapTranslates)
    }

    @Test
    fun aGestureTurnedOffStaysOff() {
        val store = FakeSettingsStore()

        TranslationSettingsRepository(store, secrets).update(
            TranslationSettings().withGestures(TranslationGestures.of(tap = null, longPress = null)),
        )

        val reopened = TranslationSettingsRepository(store, secrets).settings.value

        assertNull(reopened.gestures.tap)
        assertNull(reopened.gestures.longPress)
        assertFalse(reopened.gestures.translatesAnything)
    }

    /**
     * Nothing at all, which is the whole point of the defaults: a book that has
     * never been told to translate anything opens as a book, and the reader
     * cannot spend a penny by resting a thumb on a paragraph.
     *
     * Who would answer if they did ask is settled all the same, and settled on
     * the dictionary - the one of the two that asks nobody for anything.
     */
    @Test
    fun anUntouchedInstallDoesNotTranslateAnything() {
        val settings = TranslationSettingsRepository(FakeSettingsStore(), secrets).settings.value

        assertNull(settings.gestures.tap)
        assertNull(settings.gestures.longPress)
        assertFalse(settings.gestures.translatesAnything)
        assertFalse(settings.gestures.wordDetails)
        assertFalse(settings.notes)
        assertEquals(WordLookup.Wiktionary, settings.wordLookup)
    }
}
