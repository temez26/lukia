package io.lukia.di

import io.lukia.features.import.presentation.ImportViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val commonModule = module {

    viewModel {
        ImportViewModel(get(), get())
    }
}