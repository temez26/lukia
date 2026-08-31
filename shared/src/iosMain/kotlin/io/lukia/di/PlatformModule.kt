package io.lukia.di

import app.cash.sqldelight.db.SqlDriver
import io.lukia.core.domain.document.IncomingDocumentStore
import io.lukia.core.domain.storage.DocumentWorkspace
import io.lukia.core.domain.storage.SecretStore
import io.lukia.core.domain.storage.SettingsStore
import io.lukia.core.infrastructure.IOSDocumentWorkspace
import io.lukia.core.infrastructure.IOSIncomingDocumentStore
import io.lukia.core.infrastructure.IOSSecretStore
import io.lukia.core.infrastructure.IOSSettingsStore
import io.lukia.core.infrastructure.lukiaSqlDriver
import io.lukia.features.library.domain.DocumentAnalyzer
import io.lukia.features.library.domain.FolderScanner
import io.lukia.features.library.infrastructure.importing.IOSDocumentAnalyzer
import io.lukia.features.library.infrastructure.importing.IOSFolderScanner
import org.koin.dsl.module

val platformModule = module {

    single<DocumentAnalyzer> {
        IOSDocumentAnalyzer()
    }

    single<IncomingDocumentStore> {
        IOSIncomingDocumentStore()
    }

    single<DocumentWorkspace> {
        IOSDocumentWorkspace()
    }

    single<SettingsStore> {
        IOSSettingsStore()
    }

    single<SecretStore> {
        IOSSecretStore()
    }

    single<SqlDriver> {
        lukiaSqlDriver()
    }

    single<FolderScanner> {
        IOSFolderScanner()
    }
}
