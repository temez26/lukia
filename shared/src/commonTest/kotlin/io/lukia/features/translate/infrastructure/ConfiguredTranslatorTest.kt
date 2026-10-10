package io.lukia.features.translate.infrastructure

import io.lukia.features.translate.domain.Translation
import io.lukia.features.translate.domain.TranslationRequest
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.domain.provider.TranslationProvider
import io.lukia.features.translate.domain.settings.TargetLanguage
import io.lukia.features.translate.domain.translate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfiguredTranslatorTest {

    @Test
    fun anthropicIsAskedWhenAnthropicIsChosen() = runTest {
        assertEquals("anthropic", translatorFor(TranslationProvider.Anthropic))
    }

    /** The whole reason there are three providers and two implementations. */
    @Test
    fun openAiAndALocalServerBothGoToTheSameImplementation() = runTest {
        assertEquals("openai", translatorFor(TranslationProvider.OpenAi))
        assertEquals("openai", translatorFor(TranslationProvider.Local))
    }

    /**
     * Read per request rather than resolved once: the choice is a setting, and
     * somebody comparing two of these switches while a book is open.
     */
    @Test
    fun changingTheChoiceChangesWhoAnswersWithoutRebuildingAnything() = runTest {
        var provider = TranslationProvider.Anthropic
        val translator = ConfiguredTranslator(
            provider = { provider },
            anthropic = naming("anthropic"),
            openAiCompatible = naming("openai"),
        )

        assertEquals("anthropic", translator.translate(request()).text)

        provider = TranslationProvider.Local

        assertEquals("openai", translator.translate(request()).text)
    }

    private suspend fun translatorFor(provider: TranslationProvider): String =
        ConfiguredTranslator(
            provider = { provider },
            anthropic = naming("anthropic"),
            openAiCompatible = naming("openai"),
        ).translate(request()).text

    private fun naming(name: String) = Translator { _, _ -> Translation(name) }

    private fun request() = TranslationRequest(
        text = "Call me Ishmael.",
        targetLanguage = TargetLanguage.Finnish,
    )
}
