package io.lukia.di

import io.lukia.features.bookdetail.application.GetDocumentDetailsUseCase
import io.lukia.features.bookdetail.presentation.BookDetailViewModel
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.features.library.application.ImportFolderUseCase
import io.lukia.features.library.application.GetLibraryDocumentsUseCase
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.core.infrastructure.db.LukiaDatabase
import io.lukia.features.library.infrastructure.SqlLibraryRepository
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.library.application.SetFavoriteUseCase
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.reader.application.OpenDocumentUseCase
import io.lukia.features.reader.domain.ReadingProgressRepository
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import io.lukia.features.reader.infrastructure.SqlReadingProgressRepository
import io.lukia.features.reader.presentation.ReaderViewModel
import io.lukia.features.settings.infrastructure.AppSettingsRepository
import io.lukia.features.translate.application.TranslatePassageUseCase
import io.lukia.features.translate.domain.Translator
import io.lukia.features.translate.infrastructure.TranslationSettingsRepository
import io.lukia.features.translate.infrastructure.ConfiguredTranslator
import io.lukia.features.translate.infrastructure.anthropic.AnthropicTranslator
import io.lukia.features.translate.domain.ModelCatalogue
import io.lukia.features.translate.infrastructure.openai.OpenAiModelCatalogue
import io.lukia.features.translate.infrastructure.openai.OpenAiTranslator
import io.lukia.features.translate.infrastructure.translationHttpClient
import io.lukia.features.settings.presentation.SettingsViewModel
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

    single {
        ImportDocumentUseCase(get(), get())
    }

    single {
        ImportFolderUseCase(get(), get())
    }

    viewModel {
        LibraryViewModel(get(), get(), get(), get(), get(), get())
    }

    single {
        GetLibraryDocumentsUseCase(get())
    }

    single {
        SetFavoriteUseCase(get())
    }

    single {
        RemoveDocumentUseCase(get(), get(), get())
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

    // Likewise a single: the app's theme is read at the root, so every screen
    // repaints from the same value the moment it changes.
    single {
        AppSettingsRepository(get())
    }

    viewModel {
        SettingsViewModel(get(), get(), get(), get())
    }

    // One client for the app, so connections are kept and reused between
    // taps rather than set up again for every sentence.
    single {
        translationHttpClient()
    }

    // Which of these speaks is a setting, read per request. Everything above
    // this line is written against the port, so adding another - a model on the
    // device, a plain dictionary lookup - is one more entry here and nothing
    // else in the app.
    single<Translator> {
        val settings: TranslationSettingsRepository = get()

        ConfiguredTranslator(
            provider = { settings.settings.value.provider },
            anthropic = AnthropicTranslator(get(), settings),
            openAiCompatible = OpenAiTranslator(get(), settings)
        )
    }

    single {
        TranslatePassageUseCase(get())
    }

    // Only a server the reader points at themselves is worth asking what it can
    // run - a hosted service publishes a catalogue that barely changes, while a
    // local one runs whichever file they loaded this morning.
    single<ModelCatalogue> {
        OpenAiModelCatalogue(get(), get())
    }

    // A single, like the other settings: the language chosen on the settings
    // screen has to be the language the open book uses on the very next tap.
    single {
        TranslationSettingsRepository(get(), get())
    }

    viewModel { (location: String) ->
        BookDetailViewModel(location, get(), get(), get())
    }

    viewModel { (location: String) ->
        ReaderViewModel(location, get(), get(), get(), get(), get())
    }
}