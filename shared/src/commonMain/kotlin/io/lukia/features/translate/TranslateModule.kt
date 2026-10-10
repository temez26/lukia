package io.lukia.features.translate

import io.lukia.core.domain.text.PassageUnit
import io.lukia.features.translate.application.LookUpWordUseCase
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.CacheIdentity
import io.lukia.features.translate.domain.TranslationCache
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.domain.lookup.WordDictionary
import io.lukia.features.translate.domain.provider.ModelCatalogue
import io.lukia.features.translate.domain.provider.ModelWarmUp
import io.lukia.features.translate.domain.settings.TranslationSettings
import io.lukia.features.translate.domain.settings.TranslationSettingsRepository
import io.lukia.features.translate.infrastructure.CachingTranslator
import io.lukia.features.translate.infrastructure.CoalescingTranslator
import io.lukia.features.translate.infrastructure.ConfiguredTranslator
import io.lukia.features.translate.infrastructure.PersistedTranslationSettingsRepository
import io.lukia.features.translate.infrastructure.RetryingTranslator
import io.lukia.features.translate.infrastructure.SqlTranslationCache
import io.lukia.features.translate.infrastructure.TRANSLATION_PROMPT_VERSION
import io.lukia.features.translate.infrastructure.WordLookupTranslator
import io.lukia.features.translate.infrastructure.anthropic.AnthropicTranslator
import io.lukia.features.translate.infrastructure.google.GoogleTranslator
import io.lukia.features.translate.infrastructure.openai.OpenAiModelCatalogue
import io.lukia.features.translate.infrastructure.openai.OpenAiModelWarmUp
import io.lukia.features.translate.infrastructure.openai.OpenAiTranslator
import io.lukia.features.translate.infrastructure.translationHttpClient
import io.lukia.features.translate.infrastructure.wiktionary.SqlWiktionaryPageCache
import io.lukia.features.translate.infrastructure.wiktionary.WiktionaryDictionary
import io.lukia.features.translate.infrastructure.wiktionary.WiktionaryPageCache
import io.lukia.features.translate.infrastructure.wiktionary.WiktionaryPages
import io.lukia.features.translate.infrastructure.wiktionary.WiktionaryTranslator
import org.koin.dsl.module

val translateModule = module {

    // One client for the app, so connections are kept and reused between
    // taps rather than set up again for every sentence.
    single {
        translationHttpClient()
    }

    // Passages already paid for, kept between runs of the app.
    single<TranslationCache> {
        SqlTranslationCache(get())
    }

    // Wiki pages already read, kept between runs of the app. Free to ask for and
    // still worth keeping: one word can be four requests, and Wikimedia limits
    // how often it will answer a client that has no key.
    single<WiktionaryPageCache> {
        SqlWiktionaryPageCache(get())
    }

    // The wikis behind both dictionary answers - the line that goes over a word
    // and the whole entry the reader opens. One of these rather than two, so
    // that asking the same question twice is not two ways of asking it.
    single {
        WiktionaryPages(get(), get(), get(), get())
    }

    // What answers when a line over the word was not enough. Bound to the port
    // rather than to the class, so the reader's panel never learns which
    // dictionary it is reading.
    single<WordDictionary> {
        WiktionaryDictionary(get())
    }

    // Which of these speaks is a setting, read per request. Everything above
    // this line is written against the port, so adding another - a model on the
    // device, a plain dictionary lookup - is one more entry here and nothing
    // else in the app.
    //
    // The two wrappers around it are where translating stops costing anything it
    // need not: the same passage is asked for once, and only once ever. Both are
    // translators themselves, so nothing above knows they are there - which is
    // why the order is decided here and only here.
    single<Translator> {
        val settings: TranslationSettingsRepository = get()

        val configured = ConfiguredTranslator(
            provider = { settings.settings.value.provider },
            anthropic = AnthropicTranslator(get(), settings, get()),
            openAiCompatible = OpenAiTranslator(get(), settings, get()),
        )

        // A single word can go somewhere else entirely, and only a single word:
        // everything larger needs the book around it, which is the one thing a
        // dictionary and a bare translator do not get.
        val answering = WordLookupTranslator(
            lookup = { settings.settings.value.wordLookup },
            translator = configured,
            google = GoogleTranslator(get(), settings, get()),
            dictionary = WiktionaryTranslator(get()),
        )

        // Order matters, and is the whole of what this composition decides.
        // Asking twice at once is answered once; asking a second time is not
        // asked at all; and only what actually reaches the wire is ever asked
        // again after a failure - see RetryingTranslator for why retrying sits
        // under both of the others rather than over them.
        CoalescingTranslator(
            CachingTranslator(
                translator = RetryingTranslator(answering),
                cache = get(),
                // Named per request, because a word and a sentence do not go to
                // the same place - a cache filing both under the translator's
                // name would answer one with the other.
                //
                // And only a translator that is handed the book is filed under
                // it. A dictionary and Google get the word and the language and
                // nothing else, so `values` is the same answer in every book on
                // the shelf and is fetched once for all of them.
                answering = { request ->
                    val current = settings.settings.value

                    if (request.unit == PassageUnit.Word) {
                        CacheIdentity("word/${current.wordLookup.name}", readsTheBook = false)
                    } else {
                        CacheIdentity(
                            translator = current.translatorName(),
                            readsTheBook = true,
                            // The instructions are half the question; an answer
                            // given to an older wording is an answer to a
                            // different one - see TRANSLATION_PROMPT_VERSION.
                            promptVersion = TRANSLATION_PROMPT_VERSION,
                        )
                    }
                },
            ),
        )
    }

    single {
        TranslatePassageUseCase(get())
    }

    single {
        LookUpWordUseCase(get())
    }

    // Only a server the reader points at themselves is worth asking what it can
    // run - a hosted service publishes a catalogue that barely changes, while a
    // local one runs whichever file they loaded this morning.
    single<ModelCatalogue> {
        OpenAiModelCatalogue(get(), get(), get())
    }

    // Same reasoning, and the same provider: only a model the reader hosts has
    // to be loaded before it can answer, and only an OpenAI-compatible server
    // can be asked to.
    single<ModelWarmUp> {
        OpenAiModelWarmUp(get(), get(), get())
    }

    // A single, like the other settings: the language chosen on the settings
    // screen has to be the language the open book uses on the very next tap.
    single<TranslationSettingsRepository> {
        PersistedTranslationSettingsRepository(get(), get())
    }
}

/**
 * Who is being asked, closely enough that nobody else's answer can be mistaken
 * for theirs.
 *
 * The address is part of the name only for a server the reader points at
 * themselves: two machines on a desk both answering to `local-model` are two
 * models with one name, and a cache knowing only the name hands one machine's
 * work back as the other's. A hosted service has one address, so it says nothing.
 */
private fun TranslationSettings.translatorName(): String = when {
    provider.hasConfigurableEndpoint -> "${provider.name}/$model@$baseUrl"
    else -> "${provider.name}/$model"
}
