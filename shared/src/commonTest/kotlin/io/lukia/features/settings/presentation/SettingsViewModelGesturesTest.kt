package io.lukia.features.settings.presentation

import io.lukia.core.domain.storage.FakeSecretStore
import io.lukia.core.domain.storage.FakeSettingsStore
import io.lukia.core.domain.storage.SettingsStore
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import io.lukia.features.settings.infrastructure.AppSettingsRepository
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.provider.FakeTranslator
import io.lukia.features.translate.domain.provider.ModelCatalogue
import io.lukia.features.translate.domain.provider.ModelWarmUp
import io.lukia.features.translate.domain.text.PassageUnit
import io.lukia.features.translate.domain.text.WordLookup
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The settings screen's half of the seam between what the reader chooses and
 * what the book does with it.
 *
 * Worth testing separately from either side: the reader has been proved to obey
 * whatever gestures it is handed, and the store has been proved to remember
 * whatever it is given, and neither says anything about whether the screen puts
 * the reader's choice into the store at all.
 */
class SettingsViewModelGesturesTest {

    @Test
    fun choosingWhatATapDoesPutsItWhereTheReaderWillReadIt() {
        val store = FakeSettingsStore()
        val translation = TranslationSettingsRepository(store, FakeSecretStore())

        viewModel(store, translation).setTapGesture(PassageUnit.Paragraph)

        assertEquals(PassageUnit.Paragraph, translation.settings.value.gestures.tap)
        // And the other one is left exactly as it was.
        assertEquals(PassageUnit.Word, translation.settings.value.gestures.longPress)
    }

    @Test
    fun choosingWhatAPressAndHoldDoesLeavesTheTapAlone() {
        val store = FakeSettingsStore()
        val translation = TranslationSettingsRepository(store, FakeSecretStore())

        viewModel(store, translation).setLongPressGesture(PassageUnit.Sentence)

        assertEquals(PassageUnit.Sentence, translation.settings.value.gestures.longPress)
        assertEquals(PassageUnit.Sentence, translation.settings.value.gestures.tap)
    }

    @Test
    fun turningAGestureOffTurnsItOff() {
        val store = FakeSettingsStore()
        val translation = TranslationSettingsRepository(store, FakeSecretStore())

        viewModel(store, translation).setTapGesture(null)

        assertNull(translation.settings.value.gestures.tap)
    }

    /** Two changes in a row, because one of them overwriting the other is the way this breaks. */
    @Test
    fun bothGesturesCanBeSetOneAfterTheOther() {
        val store = FakeSettingsStore()
        val translation = TranslationSettingsRepository(store, FakeSecretStore())
        val settings = viewModel(store, translation)

        settings.setTapGesture(PassageUnit.Word)
        settings.setLongPressGesture(PassageUnit.Paragraph)

        assertEquals(PassageUnit.Word, translation.settings.value.gestures.tap)
        assertEquals(PassageUnit.Paragraph, translation.settings.value.gestures.longPress)
    }

    /**
     * The two expensive ones are off until asked for, and asking for one does
     * not ask for the other - they cost very different amounts.
     */
    @Test
    fun theExpensiveWaysOfTranslatingAreOffUntilAskedFor() {
        val store = FakeSettingsStore()
        val translation = TranslationSettingsRepository(store, FakeSecretStore())

        assertFalse(translation.settings.value.bulkTranslation.any)

        viewModel(store, translation).setTranslateOnScreen(true)

        assertTrue(translation.settings.value.bulkTranslation.onScreen)
        assertFalse(translation.settings.value.bulkTranslation.asYouRead)
    }

    @Test
    fun whatIsAskedForIsThereTheNextTimeTheAppOpens() {
        val store = FakeSettingsStore()
        val secrets = FakeSecretStore()

        viewModel(store, TranslationSettingsRepository(store, secrets))
            .setTranslateAsYouRead(true)

        val reopened = TranslationSettingsRepository(store, secrets).settings.value

        assertTrue(reopened.bulkTranslation.asYouRead)
        assertFalse(reopened.bulkTranslation.onScreen)
    }

    @Test
    fun choosingWhereWordsAreLookedUpPutsItWhereTheBookWillReadIt() {
        val store = FakeSettingsStore()
        val translation = TranslationSettingsRepository(store, FakeSecretStore())

        viewModel(store, translation).setWordLookup(WordLookup.Wiktionary)

        assertEquals(WordLookup.Wiktionary, translation.settings.value.wordLookup)
    }

    private fun viewModel(
        store: SettingsStore,
        translation: TranslationSettingsRepository,
    ) = SettingsViewModel(
        repository = AppSettingsRepository(store),
        reader = ReaderSettingsRepository(store),
        translation = translation,
        translatePassage = TranslatePassageUseCase(FakeTranslator()),
        catalogue = ModelCatalogue { emptyList() },
        warmUp = ModelWarmUp { },
    )
}
