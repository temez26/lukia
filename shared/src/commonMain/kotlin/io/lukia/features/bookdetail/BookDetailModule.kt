package io.lukia.features.bookdetail

import io.lukia.features.bookdetail.application.GetDocumentDetailsUseCase
import io.lukia.features.bookdetail.presentation.BookDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val bookDetailModule = module {

    single {
        GetDocumentDetailsUseCase(get(), get())
    }

    viewModel { (location: String) ->
        BookDetailViewModel(location, get(), get(), get(), get())
    }
}
