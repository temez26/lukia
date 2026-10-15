package io.lukia.di

import app.cash.sqldelight.db.SqlDriver
import io.lukia.core.domain.diagnostics.CrashStore
import io.lukia.core.domain.document.BookFileStore
import io.lukia.core.domain.document.IncomingDocumentStore
import io.lukia.core.domain.storage.DocumentWorkspace
import io.lukia.core.domain.storage.SecretStore
import io.lukia.core.domain.storage.SettingsStore
import io.lukia.core.infrastructure.AndroidBookFileStore
import io.lukia.core.infrastructure.AndroidDocumentWorkspace
import io.lukia.core.infrastructure.AndroidIncomingDocumentStore
import io.lukia.core.infrastructure.AndroidSecretStore
import io.lukia.core.infrastructure.AndroidSettingsStore
import io.lukia.core.infrastructure.diagnostics.AndroidCrashStore
import io.lukia.core.infrastructure.lukiaSqlDriver
import io.lukia.features.library.domain.ContentHasher
import io.lukia.features.library.domain.DocumentAnalyzer
import io.lukia.features.library.domain.FolderScanner
import io.lukia.features.library.infrastructure.BookFileReader
import io.lukia.features.library.infrastructure.importing.AndroidDocumentAnalyzer
import io.lukia.features.library.infrastructure.importing.AndroidFolderScanner
import org.koin.dsl.module

val platformModule = module {

    single<DocumentAnalyzer> {
        AndroidDocumentAnalyzer(get(), get())
    }

    // The analyser also hashes, from the same file handles - see `ContentHasher`.
    // A second instance rather than a cast: it holds nothing but its context.
    single<ContentHasher> {
        AndroidDocumentAnalyzer(get(), get())
    }

    // And opens a book for sending to a sync server - see `BookFileReader`.
    single<BookFileReader> {
        AndroidDocumentAnalyzer(get(), get())
    }

    // Where books downloaded from a sync server are written - see `BookFileStore`.
    single<BookFileStore> {
        AndroidBookFileStore(get())
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

    single<CrashStore> {
        AndroidCrashStore(get())
    }

    single<SqlDriver> {
        lukiaSqlDriver(get(), get())
    }

    single<FolderScanner> {
        AndroidFolderScanner(get())
    }
}
