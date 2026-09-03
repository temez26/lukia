package io.lukia.di

import app.cash.sqldelight.db.SqlDriver
import io.lukia.core.domain.document.IncomingDocumentStore
import io.lukia.core.domain.storage.DocumentWorkspace
import io.lukia.core.domain.storage.SecretStore
import io.lukia.core.domain.storage.SettingsStore
import io.lukia.core.infrastructure.AndroidDocumentWorkspace
import io.lukia.core.infrastructure.AndroidIncomingDocumentStore
import io.lukia.core.infrastructure.AndroidSecretStore
import io.lukia.core.infrastructure.AndroidSettingsStore
import io.lukia.core.infrastructure.lukiaSqlDriver
import io.lukia.features.library.domain.DocumentAnalyzer
import io.lukia.features.library.domain.FolderScanner
import io.lukia.features.library.infrastructure.importing.AndroidDocumentAnalyzer
import io.lukia.features.library.infrastructure.importing.AndroidFolderScanner
import org.koin.dsl.module

val platformModule = module {

    single<DocumentAnalyzer> {
        AndroidDocumentAnalyzer(get(), get())
    }

    single<IncomingDocumentStore> {
        AndroidIncomingDocumentStore(get())
    }

    single<DocumentWorkspace> {
        AndroidDocumentWorkspace(get())
    }

    single<SettingsStore> {
        AndroidSettingsStore(get())
    }

    single<SecretStore> {
        AndroidSecretStore(get())
    }

    single<SqlDriver> {
        lukiaSqlDriver(get(), get())
    }

    single<FolderScanner> {
        AndroidFolderScanner(get())
    }
}
