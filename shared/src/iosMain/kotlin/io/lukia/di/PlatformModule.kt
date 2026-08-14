package io.lukia.di

import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.FolderScanner
import io.lukia.features.import.infrastructure.IOSDocumentAnalyzer
import io.lukia.features.import.infrastructure.IOSFolderScanner
import org.koin.dsl.module

val platformModule = module {

    single<DocumentAnalyzer> {
        IOSDocumentAnalyzer()
    }

    single<FolderScanner> {
        IOSFolderScanner()
    }

}
