package io.lukia.features.settings

import io.lukia.features.settings.infrastructure.AppSettingsRepository
import io.lukia.features.settings.presentation.DiagnosticsViewModel
import io.lukia.features.settings.presentation.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {

    // A single: the app's theme is read at the root, so every screen repaints
    // from the same value the moment it changes.
    single {
        AppSettingsRepository(get())
    }

    viewModel {
        SettingsViewModel(get(), get(), get(), get(), get(), get())
    }

    // Apart from `SettingsViewModel`, which is already as long as a file here
    // gets, and shares nothing with this beyond the screen they appear on.
    viewModel {
        DiagnosticsViewModel(get(), get())
    }
}
