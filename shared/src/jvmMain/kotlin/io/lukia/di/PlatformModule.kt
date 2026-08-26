package io.lukia.di

import app.cash.sqldelight.db.SqlDriver
import io.lukia.core.domain.DocumentWorkspace
import io.lukia.core.domain.IncomingDocumentStore
import io.lukia.core.domain.SecretStore
import io.lukia.core.domain.SettingsStore
import io.lukia.core.infrastructure.JvmDocumentWorkspace
import io.lukia.core.infrastructure.JvmIncomingDocumentStore
import io.lukia.core.infrastructure.JvmSecretStore
import io.lukia.core.infrastructure.JvmSettingsStore
import io.lukia.core.infrastructure.lukiaSqlDriver
import io.lukia.features.library.domain.DocumentAnalyzer
import io.lukia.features.library.domain.FolderScanner
import io.lukia.features.library.infrastructure.importing.DesktopDocumentAnalyzer
import io.lukia.features.library.infrastructure.importing.JvmFolderScanner
import org.koin.dsl.module

val platformModule = module {

    single<DocumentAnalyzer> {
        DesktopDocumentAnalyzer()
    }

    single<IncomingDocumentStore> {
        JvmIncomingDocumentStore()
    }

    single<DocumentWorkspace> {
        JvmDocumentWorkspace()
    }

    single<SettingsStore> {
        JvmSettingsStore()
    }

    single<SecretStore> {
        JvmSecretStore()
    }

    single<SqlDriver> {
        lukiaSqlDriver()
    }

    single<FolderScanner> {
        JvmFolderScanner()
    }
}
