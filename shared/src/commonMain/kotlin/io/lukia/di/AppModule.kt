package io.lukia.di

import io.lukia.features.import.domain.ImportDocumentUseCase
import io.lukia.features.import.presentation.ImportViewModel
import io.lukia.features.library.domain.GetLibraryDocumentsUseCase
import io.lukia.features.library.domain.LibraryRepository
import io.lukia.features.library.infrastructure.InMemoryLibraryRepository
import io.lukia.features.library.presentation.LibraryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.DocumentAnalyzerImpl

val commonModule = module {

    single<LibraryRepository> {
        InMemoryLibraryRepository()
    }

    single<DocumentAnalyzer> {
        DocumentAnalyzerImpl()
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