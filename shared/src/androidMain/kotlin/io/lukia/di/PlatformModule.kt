package io.lukia.di

import io.lukia.features.import.infrastructure.AndroidDocumentAnalyzer
import io.lukia.features.import.infrastructure.AndroidFolderScanner
import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.FolderScanner
import org.koin.dsl.module

val platformModule = module {

    single<DocumentAnalyzer> {
        AndroidDocumentAnalyzer(get())
    }

    single<FolderScanner> {
        AndroidFolderScanner(get())
    }

}
