package io.lukia.features.profile

import io.lukia.features.profile.application.GetReadingSummaryUseCase
import io.lukia.features.profile.domain.ProfilePictureStore
import io.lukia.features.profile.infrastructure.ProfileRepository
import io.lukia.features.profile.infrastructure.SqlProfilePictureStore
import io.lukia.features.profile.presentation.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val profileModule = module {

    // A single: whether there is a reader at all is read at the root, where it
    // decides whether the app opens on its library or on its welcome.
    single {
        ProfileRepository(get(), get())
    }

    single<ProfilePictureStore> {
        SqlProfilePictureStore(get())
    }

    single {
        GetReadingSummaryUseCase(get(), get(), get())
    }

    viewModel {
        ProfileViewModel(get(), get())
    }
}
