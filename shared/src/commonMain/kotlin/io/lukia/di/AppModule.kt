package io.lukia.di

import io.lukia.features.import.domain.ImportDocumentUseCase
import io.lukia.features.library.domain.GetLibraryDocumentsUseCase
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.library.infrastructure.InMemoryLibraryRepository
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

    viewModel {
        LibraryViewModel(get(), get(), get())
    }

    single {
        GetLibraryDocumentsUseCase(get())
    }
}