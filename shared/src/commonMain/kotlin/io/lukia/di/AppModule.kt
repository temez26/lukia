package io.lukia.di

import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.features.bookdetail.application.GetDocumentDetailsUseCase
import io.lukia.features.bookdetail.presentation.BookDetailViewModel
import io.lukia.features.library.application.GetLibraryDocumentsUseCase
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.features.library.application.ImportDocumentsUseCase
import io.lukia.features.library.application.ImportFolderUseCase
import io.lukia.features.library.application.RefreshDocumentUseCase
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.library.application.SetFavoriteUseCase
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.library.infrastructure.LibrarySettingsRepository
import io.lukia.features.library.infrastructure.RecentSearchesRepository
import io.lukia.features.library.infrastructure.SqlLibraryRepository
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.profile.application.GetReadingSummaryUseCase
import io.lukia.features.profile.infrastructure.ProfileRepository
import io.lukia.features.profile.presentation.ProfileViewModel
import io.lukia.features.reader.application.OpenDocumentUseCase
import io.lukia.features.reader.domain.PdfLayoutRepository
import io.lukia.features.reader.domain.ReadingProgressRepository
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import io.lukia.features.reader.infrastructure.SqlPdfLayoutRepository
import io.lukia.features.reader.infrastructure.SqlReadingProgressRepository
import io.lukia.features.reader.presentation.ReaderTranslationsFactory
import io.lukia.features.reader.presentation.ReaderViewModel
import io.lukia.features.settings.infrastructure.AppSettingsRepository
import io.lukia.features.settings.presentation.SettingsViewModel
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.ModelCatalogue
import io.lukia.features.translate.domain.ModelWarmUp
import io.lukia.features.translate.domain.PassageUnit
import io.lukia.features.translate.domain.TranslationCache
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.infrastructure.CachingTranslator
import io.lukia.features.translate.infrastructure.CoalescingTranslator
import io.lukia.features.translate.infrastructure.ConfiguredTranslator
import io.lukia.features.translate.infrastructure.SqlTranslationCache
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import io.lukia.features.translate.infrastructure.WordLookupTranslator
import io.lukia.features.translate.infrastructure.anthropic.AnthropicTranslator
import io.lukia.features.translate.infrastructure.google.GoogleTranslator
import io.lukia.features.translate.infrastructure.openai.OpenAiModelCatalogue
import io.lukia.features.translate.infrastructure.openai.OpenAiModelWarmUp
import io.lukia.features.translate.infrastructure.openai.OpenAiTranslator
import io.lukia.features.translate.infrastructure.translationHttpClient
import io.lukia.features.translate.infrastructure.wiktionary.WiktionaryTranslator
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val commonModule = module {

    // One database for the whole app, opened once. Each platform's module says
    // where the file lives; everything above this line is the same code.
    single {
        LukiaDatabase(get())
    }

    single<LibraryRepository> {
        SqlLibraryRepository(get())
    }

    single<ReadingProgressRepository> {
        SqlReadingProgressRepository(get())
    }

    // Which way each PDF is read, remembered per book - a choice about one
    // document rather than a setting on the reader, so it is stored beside the
    // reading position rather than with the preferences.
    single<PdfLayoutRepository> {
        SqlPdfLayoutRepository(get())
    }

    single {
        ImportDocumentUseCase(get(), get())
    }

    single {
        ImportDocumentsUseCase(get(), get())
    }

    single {
        ImportFolderUseCase(get(), get())
    }

    viewModel {
        LibraryViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get())
    }

    single {
        GetLibraryDocumentsUseCase(get())
    }

    single {
        SetFavoriteUseCase(get())
    }

    single {
        RefreshDocumentUseCase(get(), get())
    }

    single {
        RemoveDocumentUseCase(get(), get(), get(), get())
    }

    single {
        GetDocumentDetailsUseCase(get(), get())
    }

    single {
        OpenDocumentUseCase(get(), get())
    }

    // A single instance: reader settings are one shared, live value, so a change
    // made while reading is already in place the next time a book is opened.
    single {
        ReaderSettingsRepository(get())
    }

    // Likewise: how the shelf is laid out is remembered between sessions, and
    // read back by whichever screen asks first.
    single {
        LibrarySettingsRepository(get())
    }

    // A single so that a search made on the search tab is in the history the
    // next time any screen asks for it, without either of them re-reading the
    // store.
    single {
        RecentSearchesRepository(get())
    }

    // Likewise a single: the app's theme is read at the root, so every screen
    // repaints from the same value the moment it changes.
    single {
        AppSettingsRepository(get())
    }

    // And likewise: whether there is a reader at all is read at the root, where
    // it decides whether the app opens on its library or on its welcome.
    single {
        ProfileRepository(get())
    }

    single {
        GetReadingSummaryUseCase(get(), get())
    }

    viewModel {
        ProfileViewModel(get(), get())
    }

    viewModel {
        SettingsViewModel(get(), get(), get(), get(), get(), get())
    }

    // One client for the app, so connections are kept and reused between
    // taps rather than set up again for every sentence.
    single {
        translationHttpClient()
    }

    // Passages already paid for, kept between runs of the app.
    single<TranslationCache> {
        SqlTranslationCache(get())
    }

    // Which of these speaks is a setting, read per request. Everything above
    // this line is written against the port, so adding another - a model on the
    // device, a plain dictionary lookup - is one more entry here and nothing
    // else in the app.
    //
    // The two wrappers around it are where translating stops costing anything it
    // does not have to: the same passage is asked for once, and only once ever.
    // Both are translators themselves, so nothing above this line knows they are
    // there - which is also why the order is decided here and only here.
    single<Translator> {
        val settings: TranslationSettingsRepository = get()

        val configured = ConfiguredTranslator(
            provider = { settings.settings.value.provider },
            anthropic = AnthropicTranslator(get(), settings),
            openAiCompatible = OpenAiTranslator(get(), settings),
        )

        // A single word can go somewhere else entirely, and only a single word:
        // everything larger needs the book around it, which is the one thing a
        // dictionary and a bare translator do not get.
        val answering = WordLookupTranslator(
            lookup = { settings.settings.value.wordLookup },
            translator = configured,
            google = GoogleTranslator(get(), settings),
            dictionary = WiktionaryTranslator(get(), settings),
        )

        CoalescingTranslator(
            CachingTranslator(
                translator = answering,
                cache = get(),
                // Named per request, because a word and a sentence do not go to
                // the same place - a cache filing both under the translator's
                // name would answer one with the other.
                translatorIdentity = { request ->
                    val current = settings.settings.value

                    if (request.unit == PassageUnit.Word && current.looksUpWordsElsewhere) {
                        "word/${current.wordLookup.name}"
                    } else {
                        "${current.provider.name}/${current.model}"
                    }
                },
            ),
        )
    }

    single {
        TranslatePassageUseCase(get())
    }

    // What the reader needs to translate with, minus the one thing only the
    // ViewModel has - see ReaderTranslationsFactory.
    single {
        ReaderTranslationsFactory(get(), get())
    }

    // Only a server the reader points at themselves is worth asking what it can
    // run - a hosted service publishes a catalogue that barely changes, while a
    // local one runs whichever file they loaded this morning.
    single<ModelCatalogue> {
        OpenAiModelCatalogue(get(), get())
    }

    // Same reasoning, and the same provider: only a model the reader hosts has
    // to be loaded before it can answer, and only an OpenAI-compatible server
    // can be asked to.
    single<ModelWarmUp> {
        OpenAiModelWarmUp(get(), get())
    }

    // A single, like the other settings: the language chosen on the settings
    // screen has to be the language the open book uses on the very next tap.
    single {
        TranslationSettingsRepository(get(), get())
    }

    viewModel { (location: String) ->
        BookDetailViewModel(location, get(), get(), get(), get())
    }

    viewModel { (location: String) ->
        ReaderViewModel(location, get(), get(), get(), get(), get())
    }
}
