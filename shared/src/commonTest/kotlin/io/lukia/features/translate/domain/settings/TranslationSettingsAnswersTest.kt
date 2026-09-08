package io.lukia.features.translate.domain.settings

import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.provider.TranslationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a reader can do before they have set up a translation service, which is
 * more than nothing and used to be treated as nothing.
 *
 * The reader's own panel hid the language and the gestures behind
 * [TranslationSettings.isConfigured], which asks after the model - so somebody
 * who had chosen the dictionary had a working feature with no way to reach any
 * of its settings. The two questions are separate now, and this is the seam
 * between them.
 */
class TranslationSettingsAnswersTest {

    /** A fresh install: the translator is the word lookup, and it has no key. */
    private val untouched = TranslationSettings()

    @Test
    fun aFreshInstallHasNothingThatCanAnswer() {
        assertFalse(untouched.isConfigured)
        assertFalse(untouched.answersAnything)
    }

    @Test
    fun aDictionaryIsSomethingThatCanAnswer() {
        val dictionary = untouched.withWordLookup(WordLookup.Wiktionary)

        assertFalse(dictionary.isConfigured, "a dictionary is not a translation service")
        assertTrue(dictionary.answersAnything)
    }

    /**
     * And so is Google, which needs a key only for its supported endpoint - not
     * to answer at all.
     */
    @Test
    fun soIsGoogleWithNoKeyAtAll() {
        val google = untouched.withWordLookup(WordLookup.Google)

        assertTrue(google.answersAnything)
    }

    @Test
    fun aTranslationServiceWithAKeyAnswersEverything() {
        val configured = untouched.withApiKey("sk-test")

        assertTrue(configured.isConfigured)
        assertTrue(configured.answersAnything)
    }

    /**
     * The two expensive ways of translating are the exception: both work a
     * paragraph at a time, which only the translation service does. A dictionary
     * cannot carry them, so they are not offered beside one however the flags
     * were left.
     */
    @Test
    fun theExpensiveWaysNeedTheServiceRatherThanAnyAnswer() {
        val everything = BulkTranslation(onScreen = true, asYouRead = true)
        val dictionary = untouched
            .withWordLookup(WordLookup.Wiktionary)
            .withBulkTranslation(everything)

        assertEquals(BulkTranslation.Default, dictionary.offeredBulkTranslation)
        assertEquals(everything, dictionary.withApiKey("sk-test").offeredBulkTranslation)
    }

    /**
     * A key that is taken away takes them with it. Left as the reader set them,
     * an install whose key was cleared would go on offering a button that can
     * only fail.
     */
    @Test
    fun andAreTakenBackWhenTheServiceGoesAway() {
        val configured = untouched
            .withApiKey("sk-test")
            .withBulkTranslation(BulkTranslation(onScreen = true))

        assertEquals(
            BulkTranslation.Default,
            configured.withApiKey("").offeredBulkTranslation,
        )
    }

    /** A local server needs no key, so it is set up as soon as it is chosen. */
    @Test
    fun aLocalServerIsSetUpWithoutOne() {
        val local = untouched.copy(provider = TranslationProvider.Local)

        assertTrue(local.isConfigured)
    }
}
