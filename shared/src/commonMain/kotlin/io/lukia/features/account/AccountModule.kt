package io.lukia.features.account

import io.lukia.features.account.application.AccountSession
import io.lukia.features.account.application.ServerAdministration
import io.lukia.features.account.domain.AccountStore
import io.lukia.features.account.domain.ServerApi
import io.lukia.features.account.infrastructure.KtorServerApi
import io.lukia.features.account.infrastructure.PersistedAccountStore
import io.lukia.features.account.infrastructure.serverHttpClient
import io.lukia.features.account.presentation.AccountViewModel
import io.lukia.features.account.presentation.AdministrationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val accountModule = module {

    single<ServerApi> {
        KtorServerApi(serverHttpClient())
    }

    single<AccountStore> {
        PersistedAccountStore(get(), get())
    }

    // A single: there is one sign-in, and the sync engine and the settings page
    // have to see the same one the moment it changes.
    single {
        AccountSession(get(), get())
    }

    single {
        ServerAdministration(get(), get())
    }

    viewModel {
        AccountViewModel(get())
    }

    viewModel {
        AdministrationViewModel(get())
    }
}
