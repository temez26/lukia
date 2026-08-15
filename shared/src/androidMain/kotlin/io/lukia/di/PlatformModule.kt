package io.lukia.di

import io.lukia.core.domain.DocumentWorkspace
import io.lukia.core.infrastructure.AndroidDocumentWorkspace
import io.lukia.features.import.infrastructure.AndroidDocumentAnalyzer
import io.lukia.features.import.infrastructure.AndroidFolderScanner
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.FolderScanner
import org.koin.dsl.module

val platformModule = module {

    single<DocumentAnalyzer> {
        AndroidDocumentAnalyzer(get())
    }

    single<DocumentWorkspace> {
        AndroidDocumentWorkspace(get())
    }

    single<FolderScanner> {
        AndroidFolderScanner(get())
    }

}
