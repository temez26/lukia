package io.lukia.di

import app.cash.sqldelight.db.SqlDriver
import io.lukia.core.domain.diagnostics.CrashStore
import io.lukia.core.domain.document.BookFileStore
import io.lukia.core.domain.document.IncomingDocumentStore
import io.lukia.core.domain.storage.DocumentWorkspace
import io.lukia.core.domain.storage.SecretStore
import io.lukia.core.domain.storage.SettingsStore
import io.lukia.core.infrastructure.DesktopWindowBounds
import io.lukia.core.infrastructure.JvmBookFileStore
import io.lukia.core.infrastructure.JvmDocumentWorkspace
import io.lukia.core.infrastructure.JvmIncomingDocumentStore
import io.lukia.core.infrastructure.JvmSecretStore
import io.lukia.core.infrastructure.JvmSettingsStore
import io.lukia.core.infrastructure.diagnostics.JvmCrashStore
import io.lukia.core.infrastructure.lukiaSqlDriver
import io.lukia.features.library.domain.ContentHasher
import io.lukia.features.library.domain.DocumentAnalyzer
import io.lukia.features.library.domain.FolderScanner
import io.lukia.features.library.infrastructure.importing.DesktopDocumentAnalyzer
import io.lukia.features.library.infrastructure.importing.JvmFolderScanner
import org.koin.dsl.module

val platformModule = module {

    // Desktop-only: no other platform has a window whose size is the app's to
    // remember.
    single {
        DesktopWindowBounds(get())
    }

    single<DocumentAnalyzer> {
        DesktopDocumentAnalyzer(get())
    }

    // The analyser also hashes, from the same file handles - see `ContentHasher`.
    // A second instance rather than a cast: it holds nothing but its context.
    single<ContentHasher> {
        DesktopDocumentAnalyzer(get())
    }

    // Where books downloaded from a sync server are written - see `BookFileStore`.
    single<BookFileStore> {
        JvmBookFileStore()
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

    single<CrashStore> {
        JvmCrashStore()
    }

    single<SqlDriver> {
        lukiaSqlDriver(diagnostics = get())
    }

    single<FolderScanner> {
        JvmFolderScanner()
    }
}
