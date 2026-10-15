package io.lukia.di

import app.cash.sqldelight.db.SqlDriver
import io.lukia.core.domain.diagnostics.CrashStore
import io.lukia.core.domain.document.BookFileStore
import io.lukia.core.domain.document.IncomingDocumentStore
import io.lukia.core.domain.storage.DocumentWorkspace
import io.lukia.core.domain.storage.SecretStore
import io.lukia.core.domain.storage.SettingsStore
import io.lukia.core.infrastructure.IOSBookFileStore
import io.lukia.core.infrastructure.IOSDocumentWorkspace
import io.lukia.core.infrastructure.IOSIncomingDocumentStore
import io.lukia.core.infrastructure.IOSSecretStore
import io.lukia.core.infrastructure.IOSSettingsStore
import io.lukia.core.infrastructure.diagnostics.IOSCrashStore
import io.lukia.core.infrastructure.lukiaSqlDriver
import io.lukia.features.library.domain.ContentHasher
import io.lukia.features.library.domain.DocumentAnalyzer
import io.lukia.features.library.domain.FolderScanner
import io.lukia.features.library.infrastructure.BookFileReader
import io.lukia.features.library.infrastructure.importing.IOSDocumentAnalyzer
import io.lukia.features.library.infrastructure.importing.IOSFolderScanner
import org.koin.dsl.module

val platformModule = module {

    single<DocumentAnalyzer> {
        IOSDocumentAnalyzer(get())
    }

    // The analyser also hashes, from the same file handles - see `ContentHasher`.
    // A second instance rather than a cast: it holds nothing but its context.
    single<ContentHasher> {
        IOSDocumentAnalyzer(get())
    }

    // And opens a book for sending to a sync server - see `BookFileReader`.
    single<BookFileReader> {
        IOSDocumentAnalyzer(get())
    }

    // Where books downloaded from a sync server are written - see `BookFileStore`.
    single<BookFileStore> {
        IOSBookFileStore()
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

    single<CrashStore> {
        IOSCrashStore()
    }

    single<SqlDriver> {
        lukiaSqlDriver(diagnostics = get())
    }

    single<FolderScanner> {
        IOSFolderScanner()
    }
}
