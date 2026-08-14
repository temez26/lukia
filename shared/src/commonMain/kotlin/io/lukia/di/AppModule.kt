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
        RemoveDocumentUseCase(get())
    }

    single {
        GetDocumentDetailsUseCase(get())
    }

    viewModel { (location: String) ->
        BookDetailViewModel(location, get(), get(), get())
    }
}