package io.lukia.di

import io.lukia.features.import.domain.DocumentAnalyzer
import io.lukia.features.import.domain.FolderScanner
import io.lukia.features.import.infrastructure.DesktopDocumentAnalyzer
import io.lukia.features.import.infrastructure.JvmFolderScanner
import org.koin.dsl.module

val platformModule = module {

    single<DocumentAnalyzer> {
        DesktopDocumentAnalyzer()
    }

    single<FolderScanner> {
        JvmFolderScanner()
    }
}
