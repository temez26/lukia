package io.lukia.features.translate.infrastructure

import io.lukia.core.domain.FakeSecretStore
import io.lukia.core.domain.FakeSettingsStore
import io.lukia.features.translate.domain.TargetLanguage
import io.lukia.features.translate.domain.TranslationProvider
import io.lukia.features.translate.domain.TranslationSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TranslationSettingsRepositoryTest {

    /**
     * One per test, and shared between the repositories a test opens in turn -
     * so reopening reads back what the first one stored, exactly as a second
     * launch of the app would.
     */
    private val secrets = FakeSecretStore()

    @Test
    fun anUntouchedInstallStartsAtTheDefaults() {
        val settings = TranslationSettingsRepository(FakeSettingsStore(), secrets).settings.value

        assertEquals(TargetLanguage.Default, settings.targetLanguage)
        assertEquals(TranslationProvider.Default, settings.provider)
        assertEquals(TranslationProvider.Anthropic.defaultModel, settings.model)
        assertEquals(TranslationProvider.Anthropic.defaultBaseUrl, settings.baseUrl)
        assertNull(settings.apiKey)
        assertFalse(settings.isConfigured)
    }

    @Test
    fun whatIsChosenIsThereTheNextTimeTheAppOpens() {
        val store = FakeSettingsStore()

        TranslationSettingsRepository(store, secrets).update(
            TranslationSettings(
                targetLanguage = TargetLanguage.Finnish,
                provider = TranslationProvider.OpenAi
            ).withModel("gpt-4o-mini").withApiKey("sk-test")
        )

        val reopened = TranslationSettingsRepository(store, secrets).settings.value

        assertEquals(TargetLanguage.Finnish, reopened.targetLanguage)
        assertEquals(TranslationProvider.OpenAi, reopened.provider)
        assertEquals("gpt-4o-mini", reopened.model)
        assertEquals("sk-test", reopened.apiKey)
        assertTrue(reopened.isConfigured)
    }

    /**
     * How long a provider may go quiet is per provider like everything else,
     * and its default is the provider's own: a hosted service and a machine
     * loading a model off disk are not waits of the same size.
     */
    @Test
    fun eachProviderStartsAtItsOwnWait() {
        val settings = TranslationSettingsRepository(FakeSettingsStore(), secrets).settings.value

        assertEquals(TranslationProvider.Anthropic.defaultTimeoutSeconds, settings.timeoutSeconds)
        assertEquals(
            TranslationProvider.Local.defaultTimeoutSeconds,
            settings.timeoutSecondsFor(TranslationProvider.Local)
        )
        assertTrue(
            TranslationProvider.Local.defaultTimeoutSeconds >
                TranslationProvider.Anthropic.defaultTimeoutSeconds,
            "a local model is given no longer to load than a hosted one takes to answer"
        )
    }

    @Test
    fun aWaitTheReaderSetIsThereTheNextTimeTheAppOpens() {
        val store = FakeSettingsStore()

        TranslationSettingsRepository(store, secrets).update(
            TranslationSettings(provider = TranslationProvider.Local).withTimeoutSeconds(600)
        )

        val reopened = TranslationSettingsRepository(store, secrets).settings.value

        assertEquals(600, reopened.timeoutSeconds)
    }

    /**
     * The field this comes from is text, and a reader who typed a zero meant
     * "quickly" rather than "give up before asking".
     */
    @Test
    fun aWaitTooShortToBeMeantIsBroughtBackIntoRange() {
        val settings = TranslationSettings(provider = TranslationProvider.Local)

        assertEquals(
            TranslationSettings.TimeoutSecondsRange.first,
            settings.withTimeoutSeconds(0).timeoutSeconds
        )
        assertEquals(
            TranslationSettings.TimeoutSecondsRange.last,
            settings.withTimeoutSeconds(999_999).timeoutSeconds
        )
    }

    /**
     * The reason everything is stored per provider: somebody comparing a local
     * model against a hosted one switches back and forth repeatedly, and losing
     * a key on every switch would make that unbearable.
     */
    @Test
    fun eachProviderKeepsItsOwnKeyModelAndAddress() {
        val store = FakeSettingsStore()

        val configured = TranslationSettings()
            .withApiKey("sk-ant-test")
            .withModel("claude-sonnet-5")
            .copy(provider = TranslationProvider.Local)
            .withBaseUrl("http://192.168.1.50:1234/v1")
            .withModel("qwen2.5-7b-instruct")

        TranslationSettingsRepository(store, secrets).update(configured)
        val reopened = TranslationSettingsRepository(store, secrets).settings.value

        assertEquals("qwen2.5-7b-instruct", reopened.model)
        assertEquals("http://192.168.1.50:1234/v1", reopened.baseUrl)
        assertNull(reopened.apiKey, "the local server borrowed Anthropic's key")

        val backToAnthropic = reopened.copy(provider = TranslationProvider.Anthropic)

        assertEquals("sk-ant-test", backToAnthropic.apiKey)
        assertEquals("claude-sonnet-5", backToAnthropic.model)
    }

    /** Emptying a field is how somebody undoes it, on disk as well as in memory. */
    @Test
    fun aClearedValueDoesNotComeBack() {
        val store = FakeSettingsStore()
        val repository = TranslationSettingsRepository(store, secrets)

        repository.update(TranslationSettings().withApiKey("sk-ant-test"))
        repository.update(repository.settings.value.withApiKey(""))

        assertNull(TranslationSettingsRepository(store, secrets).settings.value.apiKey)
    }

    @Test
    fun aValueOfNothingButSpacesIsNoValue() {
        val settings = TranslationSettings().withApiKey("   ").withModel("  ")

        assertNull(settings.apiKey)
        assertEquals(TranslationProvider.Anthropic.defaultModel, settings.model)
    }

    /**
     * The key is kept out of the settings file now. Somebody upgrading from a
     * build that kept it there should not have to paste it again - and it must
     * not be left behind in plain text once it has somewhere better to live.
     */
    @Test
    fun aKeyLeftInTheSettingsFileByAnOlderBuildIsMovedOutOfIt() {
        val store = FakeSettingsStore().apply {
            putString("translation.apiKey.Anthropic", "sk-ant-old")
        }

        val settings = TranslationSettingsRepository(store, secrets).settings.value

        assertEquals("sk-ant-old", settings.apiKey)
        assertEquals("sk-ant-old", secrets.getSecret("translation.apiKey.Anthropic"))
        assertEquals("", store.getString("translation.apiKey.Anthropic"))
    }

    /** A key never touches the settings file on the way in. */
    @Test
    fun aKeyIsNotWrittenToTheSettingsFile() {
        val store = FakeSettingsStore()

        TranslationSettingsRepository(store, secrets)
            .update(TranslationSettings().withApiKey("sk-ant-test"))

        assertEquals("", store.getString("translation.apiKey.Anthropic").orEmpty())
        assertEquals("sk-ant-test", secrets.getSecret("translation.apiKey.Anthropic"))
    }

    /**
     * A value written by an older build, or edited by hand into nonsense, falls
     * back to that setting's default rather than taking the rest down with it.
     */
    @Test
    fun anUnreadableChoiceFallsBackWithoutLosingTheOthers() {
        val store = FakeSettingsStore().apply {
            putString("translation.targetLanguage", "Klingon")
            putString("translation.provider", "Telepathy")
            putString("translation.apiKey.Anthropic", "sk-ant-test")
        }

        val settings = TranslationSettingsRepository(store, secrets).settings.value

        assertEquals(TargetLanguage.Default, settings.targetLanguage)
        assertEquals(TranslationProvider.Default, settings.provider)
        assertEquals("sk-ant-test", settings.apiKey)
    }

    /** A local server needs no key, so having none is not being unconfigured. */
    @Test
    fun aLocalServerIsReadyWithoutAKey() {
        val settings = TranslationSettings(provider = TranslationProvider.Local)

        assertNull(settings.apiKey)
        assertTrue(settings.isConfigured)
    }

    /** Pointing plain HTTP at the open internet is not a setup this will use. */
    @Test
    fun aCleartextAddressOnThePublicInternetIsNotConfigured() {
        val settings = TranslationSettings(provider = TranslationProvider.Local)
            .withBaseUrl("http://models.example.com/v1")

        assertFalse(settings.isEndpointAllowed)
        assertFalse(settings.isConfigured)
    }

    @Test
    fun theLiveValueIsWhatWasJustChosen() {
        val repository = TranslationSettingsRepository(FakeSettingsStore(), secrets)

        repository.update(TranslationSettings(targetLanguage = TargetLanguage.Japanese))

        assertEquals(TargetLanguage.Japanese, repository.settings.value.targetLanguage)
    }
}
