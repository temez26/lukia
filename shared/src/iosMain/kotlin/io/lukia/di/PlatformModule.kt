package io.lukia.di

import app.cash.sqldelight.db.SqlDriver
import io.lukia.core.domain.DocumentWorkspace
import io.lukia.core.domain.SecretStore
import io.lukia.core.domain.SettingsStore
import io.lukia.core.infrastructure.IOSDocumentWorkspace
import io.lukia.core.infrastructure.IOSSecretStore
import io.lukia.core.infrastructure.IOSSettingsStore
import io.lukia.core.infrastructure.lukiaSqlDriver
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.FolderScanner
import io.lukia.features.import.infrastructure.IOSDocumentAnalyzer
import io.lukia.features.import.infrastructure.IOSFolderScanner
import org.koin.dsl.module

val platformModule = module {

    single<DocumentAnalyzer> {
        IOSDocumentAnalyzer()
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
