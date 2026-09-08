package io.lukia.features.translate.domain.settings

import io.lukia.features.translate.domain.BulkTranslation
import io.lukia.features.translate.domain.provider.TranslationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What a reader can do before they have set up a translation service, and what
 * they still cannot.
 *
 * A word they can always ask about: both of [WordLookup]'s answers are web APIs
 * that need nothing, which is why the reader offers the language and the
 * gestures on an install nobody has configured. Everything larger than a word
 * has nowhere to go but a model, and this is the seam between the two.
 */
class TranslationSettingsAnswersTest {

    /** A fresh install: the dictionary, no service, and AI switched off. */
    private val untouched = TranslationSettings()

    /** The same install with the one switch on, which is where the rest begins. */
    private val asked = untouched.withAiTranslation(true)

    @Test
    fun aFreshInstallHasNoTranslationService() {
        assertFalse(untouched.isConfigured)
        assertEquals(WordLookup.Wiktionary, untouched.wordLookup)
    }

    @Test
    fun aTranslationServiceWithAKeyIsSetUp() {
        assertTrue(asked.withApiKey("sk-test").isConfigured)
    }

    /**
     * And a key alone is not enough. The switch is over the whole of it, so an
     * install carrying a key from before it was turned off stays turned off.
     */
    @Test
    fun butNotUntilTheSwitchIsOn() {
        assertFalse(untouched.withApiKey("sk-test").isConfigured)
    }

    /** A local server needs no key, so it is set up as soon as it is chosen. */
    @Test
    fun aLocalServerIsSetUpWithoutOne() {
        assertTrue(asked.copy(provider = TranslationProvider.Local).isConfigured)
    }

    /**
     * The two expensive ways of translating need the service and not merely an
     * answer: both work a paragraph at a time, which no dictionary does. They
     * are not offered beside one however the flags were left.
     */
    @Test
    fun theExpensiveWaysNeedTheService() {
        val everything = BulkTranslation(onScreen = true, asYouRead = true)
        val noService = asked.withBulkTranslation(everything)

        assertEquals(BulkTranslation.Default, noService.offeredBulkTranslation)
        assertEquals(everything, noService.withApiKey("sk-test").offeredBulkTranslation)
    }

    /**
     * And a key that is taken away takes them with it. Left as the reader set
     * them, an install whose key was cleared would go on offering a button that
     * can only fail.
     */
    @Test
    fun andAreTakenBackWhenTheServiceGoesAway() {
        val configured = asked
            .withApiKey("sk-test")
            .withBulkTranslation(BulkTranslation(onScreen = true))

        assertEquals(
            BulkTranslation.Default,
            configured.withApiKey("").offeredBulkTranslation,
        )
    }

    /** Or when the switch does, which is the point of having one switch. */
    @Test
    fun orWhenTheSwitchDoes() {
        val configured = asked
            .withApiKey("sk-test")
            .withBulkTranslation(BulkTranslation(onScreen = true))

        assertEquals(
            BulkTranslation.Default,
            configured.withAiTranslation(false).offeredBulkTranslation,
        )
    }

    /**
     * And nothing is thrown away by turning it off - the key and the raised
     * gesture are still there, and come back with the switch.
     */
    @Test
    fun andTurningItOffKeepsWhatWasSetUp() {
        val configured = asked
            .withApiKey("sk-test")
            .withBulkTranslation(BulkTranslation(onScreen = true))

        val again = configured.withAiTranslation(false).withAiTranslation(true)

        assertEquals(configured, again)
    }
}
