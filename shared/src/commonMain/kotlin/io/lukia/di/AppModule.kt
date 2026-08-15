package io.lukia.di

import io.lukia.features.bookdetail.application.GetDocumentDetailsUseCase
import io.lukia.features.bookdetail.presentation.BookDetailViewModel
import io.lukia.features.library.application.ImportDocumentUseCase
import io.lukia.features.library.application.ImportFolderUseCase
import io.lukia.features.library.application.GetLibraryDocumentsUseCase
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.library.infrastructure.InMemoryLibraryRepository
import io.lukia.features.library.application.RemoveDocumentUseCase
import io.lukia.features.library.application.SetFavoriteUseCase
import io.lukia.features.library.presentation.LibraryViewModel
import io.lukia.features.reader.application.OpenDocumentUseCase
import io.lukia.features.reader.infrastructure.ReaderSettingsRepository
import io.lukia.features.reader.presentation.ReaderViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val commonModule = module {

    single<LibraryRepository> {
        InMemoryLibraryRepository()
    }

    single {
        ImportDocumentUseCase(get(), get())
    }

    single {
        ImportFolderUseCase(get(), get())
    }

    viewModel {
        LibraryViewModel(get(), get(), get(), get(), get())
    }

    single {
        GetLibraryDocumentsUseCase(get())
    }

    single {
        SetFavoriteUseCase(get())
    }

    single {
        RemoveDocumentUseCase(get(), get())
    }

    single {
        GetDocumentDetailsUseCase(get())
    }

    single {
        OpenDocumentUseCase(get(), get())
    }

    // A single instance: reader settings are one shared, live value, so a change
    // made while reading is already in place the next time a book is opened.
    single {
        ReaderSettingsRepository(get())
    }

    viewModel { (location: String) ->
        BookDetailViewModel(location, get(), get(), get())
    }

    viewModel { (location: String) ->
        ReaderViewModel(location, get(), get())
    }
}