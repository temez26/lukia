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
        SettingsViewModel(get())
    }

    viewModel { (location: String) ->
        BookDetailViewModel(location, get(), get(), get())
    }

    viewModel { (location: String) ->
        ReaderViewModel(location, get(), get(), get())
    }
}